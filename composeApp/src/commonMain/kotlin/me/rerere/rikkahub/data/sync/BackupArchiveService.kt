package me.rerere.rikkahub.data.sync

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.delete
import io.github.vinceglb.filekit.div
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.filesDir
import io.github.vinceglb.filekit.cacheDir
import io.github.vinceglb.filekit.isDirectory
import io.github.vinceglb.filekit.isRegularFile
import io.github.vinceglb.filekit.list
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.parent
import io.github.vinceglb.filekit.sink
import io.github.vinceglb.filekit.source
import io.github.vinceglb.filekit.toKotlinxIoPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemPathSeparator
import kotlinx.serialization.json.Json
import me.rerere.common.archive.PlatformZipArchive
import me.rerere.common.archive.ZipArchiveEntry
import me.rerere.common.archive.ZipArchiveWriter
import me.rerere.common.archive.ZipEntryPathPolicy
import me.rerere.common.archive.addText
import me.rerere.common.archive.readText
import me.rerere.common.crypto.PlatformSha256Crypto
import me.rerere.common.time.toCompactFileTimestamp
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.migration.SettingsJsonMigrator
import me.rerere.rikkahub.data.files.FileFolders
import me.rerere.rikkahub.data.repository.BackupSettingsGateway
import kotlin.time.Clock
import kotlin.uuid.Uuid

data class BackupFileLayout(
    val filesRoot: PlatformFile,
    val cacheRoot: PlatformFile,
    val databaseFiles: Map<String, PlatformFile> = emptyMap(),
) {
    companion object {
        fun create(databaseFile: PlatformFile? = null): BackupFileLayout = BackupFileLayout(
            filesRoot = FileKit.filesDir,
            cacheRoot = FileKit.cacheDir,
            databaseFiles = databaseFile?.let(::databaseArchiveEntries).orEmpty(),
        )

        private fun databaseArchiveEntries(databaseFile: PlatformFile): Map<String, PlatformFile> {
            val parent = requireNotNull(databaseFile.parent()) { "Database file must have a parent directory" }
            return mapOf(
                DATABASE_ENTRY to databaseFile,
                DATABASE_WAL_ENTRY to (parent / "${databaseFile.name}-wal"),
                DATABASE_SHM_ENTRY to (parent / "${databaseFile.name}-shm"),
            )
        }
    }
}

class BackupArchiveService(
    private val settingsGateway: BackupSettingsGateway,
    private val json: Json,
    private val layout: BackupFileLayout,
) {
    suspend fun prepareArchive(includeDatabase: Boolean, includeFiles: Boolean): PlatformFile =
        withContext(Dispatchers.IO) {
            layout.cacheRoot.createDirectories()
            layout.filesRoot.createDirectories()
            val archive = layout.cacheRoot / "backup_${Clock.System.now().toCompactFileTimestamp()}.zip"
            archive.delete(mustExist = false)
            try {
                PlatformZipArchive.create(archive.sink().buffered()) {
                    addText(SETTINGS_ENTRY, json.encodeToString(settingsGateway.settings.value))
                    if (includeDatabase) addDatabaseEntries()
                    if (includeFiles) addApplicationFiles()
                }
                archive
            } catch (error: Throwable) {
                archive.delete(mustExist = false)
                throw error
            }
        }

    suspend fun restoreArchive(
        archive: PlatformFile,
        includeDatabase: Boolean,
        includeFiles: Boolean,
    ): Unit = withContext(Dispatchers.IO) {
        require(archive.exists() && archive.isRegularFile()) { "Backup file does not exist or is not readable" }
        layout.filesRoot.createDirectories()
        PlatformZipArchive.read(archive.source().buffered()) { entry ->
            val path = ZipEntryPathPolicy.normalizeOrNull(entry.name)
                ?: throw IllegalArgumentException("Unsafe ZIP entry path")
            when {
                path == SETTINGS_ENTRY -> restoreSettings(entry)
                includeDatabase && path in layout.databaseFiles -> {
                    entry.copyToSafeTarget(layout.databaseFiles.getValue(path), allowedRoot = null)
                }

                includeFiles && isRestorableApplicationPath(path) -> {
                    val target = layout.filesRoot / path
                    entry.copyToSafeTarget(target, allowedRoot = layout.filesRoot)
                }
            }
        }
    }

    fun createTemporaryArchive(prefix: String = "restore"): PlatformFile {
        layout.cacheRoot.createDirectories()
        return layout.cacheRoot / "$prefix-${Uuid.random()}.zip"
    }

    suspend fun sha256Hex(file: PlatformFile): String = withContext(Dispatchers.IO) {
        PlatformSha256Crypto.newDigest().run {
            file.source().buffered().use { source ->
                val buffer = ByteArray(STREAM_BUFFER_SIZE)
                while (true) {
                    val read = source.readAtMostTo(buffer, 0, buffer.size)
                    if (read < 0) break
                    if (read > 0) update(buffer, 0, read)
                }
            }
            digest().toHexString()
        }
    }

    private fun ZipArchiveWriter.addDatabaseEntries() {
        layout.databaseFiles.forEach { (entryName, file) ->
            if (file.exists() && file.isRegularFile()) {
                add(entryName, file.source().buffered())
            }
        }
    }

    private fun ZipArchiveWriter.addApplicationFiles() {
        addDirectChildren(layout.filesRoot / FileFolders.UPLOAD, "${FileFolders.UPLOAD}/")
        addTree(layout.filesRoot / FileFolders.SKILLS, "${FileFolders.SKILLS}/")
        addDirectChildren(layout.filesRoot / FileFolders.FONTS, "${FileFolders.FONTS}/")
    }

    private fun ZipArchiveWriter.addDirectChildren(directory: PlatformFile, prefix: String) {
        if (!directory.isSafeDirectoryInside(layout.filesRoot)) return
        directory.list().forEach { child ->
            if (child.isSafeRegularFileInside(directory)) {
                add("$prefix${child.name}", child.source().buffered())
            }
        }
    }

    private fun ZipArchiveWriter.addTree(root: PlatformFile, prefix: String) {
        if (!root.isSafeDirectoryInside(layout.filesRoot)) return
        val visited = mutableSetOf<String>()

        fun visit(directory: PlatformFile, relativePrefix: String) {
            val resolved = directory.resolvedPathOrNull() ?: return
            if (!visited.add(resolved)) return
            directory.list().forEach { child ->
                when {
                    child.isSafeDirectoryInside(root) -> visit(child, "$relativePrefix${child.name}/")
                    child.isSafeRegularFileInside(root) -> {
                        add("$prefix$relativePrefix${child.name}", child.source().buffered())
                    }
                }
            }
        }

        visit(root, "")
    }

    private suspend fun restoreSettings(entry: ZipArchiveEntry) {
        val migrated = SettingsJsonMigrator.migrate(entry.readText())
        settingsGateway.update(json.decodeFromString<Settings>(migrated))
    }

    private fun isRestorableApplicationPath(path: String): Boolean =
        ZipEntryPathPolicy.directChildOfOrNull(path, FileFolders.UPLOAD) != null ||
            ZipEntryPathPolicy.directChildOfOrNull(path, FileFolders.FONTS) != null ||
            ZipEntryPathPolicy.relativeToRootOrNull(path, FileFolders.SKILLS)
                ?.let(::isValidSkillRelativePath) == true

    private fun isValidSkillRelativePath(relativePath: String): Boolean {
        val segments = relativePath.split('/')
        return segments.size >= 2 && segments.all { segment ->
            segment.isNotBlank() && segment != "." && segment != ".." &&
                '/' !in segment && '\\' !in segment && segment.none(Char::isISOControl)
        }
    }

    private fun ZipArchiveEntry.copyToSafeTarget(target: PlatformFile, allowedRoot: PlatformFile?) {
        val parent = requireNotNull(target.parent()) { "Backup target has no parent: ${target.name}" }
        parent.createDirectories()
        if (allowedRoot != null) {
            require(parent.isResolvedInside(allowedRoot)) { "Unsafe backup target: ${target.name}" }
            if (target.exists()) {
                require(target.isResolvedInside(allowedRoot)) { "Unsafe backup target: ${target.name}" }
            }
        }
        target.sink().buffered().use { sink -> copyTo(sink) }
    }
}

private fun PlatformFile.isSafeDirectoryInside(root: PlatformFile): Boolean =
    exists() && isDirectory() && isResolvedInside(root)

private fun PlatformFile.isSafeRegularFileInside(root: PlatformFile): Boolean =
    exists() && isRegularFile() && isResolvedInside(root)

private fun PlatformFile.isResolvedInside(root: PlatformFile): Boolean {
    val resolvedRoot = root.resolvedPathOrNull() ?: return false
    val resolvedCandidate = resolvedPathOrNull() ?: return false
    return resolvedCandidate == resolvedRoot ||
        resolvedCandidate.startsWith(resolvedRoot.trimEnd(SystemPathSeparator) + SystemPathSeparator)
}

private fun PlatformFile.resolvedPathOrNull(): String? =
    runCatching { SystemFileSystem.resolve(toKotlinxIoPath()).toString() }.getOrNull()

private const val SETTINGS_ENTRY = "settings.json"
private const val DATABASE_ENTRY = "rikka_hub.db"
private const val DATABASE_WAL_ENTRY = "rikka_hub-wal"
private const val DATABASE_SHM_ENTRY = "rikka_hub-shm"
private const val STREAM_BUFFER_SIZE = 8_192
