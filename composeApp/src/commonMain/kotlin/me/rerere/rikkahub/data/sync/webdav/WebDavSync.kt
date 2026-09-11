package me.rerere.rikkahub.data.sync.webdav

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.source
import io.github.vinceglb.filekit.toKotlinxIoPath
import io.ktor.client.HttpClient
import io.ktor.utils.io.ByteReadChannel
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.io.Buffer
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemPathSeparator
import kotlinx.serialization.json.Json
import me.rerere.common.archive.PlatformZipArchive
import me.rerere.common.archive.ZipArchiveEntry
import me.rerere.common.archive.ZipArchiveWriter
import me.rerere.common.archive.ZipEntryPathPolicy
import me.rerere.common.archive.addText
import me.rerere.common.archive.readText
import me.rerere.common.logging.RikkaLog as Log
import me.rerere.common.time.toCompactFileTimestamp
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.WebDavConfig
import me.rerere.rikkahub.data.datastore.migration.SettingsJsonMigrator
import me.rerere.rikkahub.data.files.FileFolders
import me.rerere.rikkahub.data.files.SkillPaths
import me.rerere.rikkahub.data.sync.BackupFileLayout
import me.rerere.rikkahub.data.sync.BackupZipPathResolver
import me.rerere.rikkahub.data.sync.BackupZipSourcePolicy
import me.rerere.rikkahub.utils.canRead
import me.rerere.rikkahub.utils.delete
import me.rerere.rikkahub.utils.exists
import me.rerere.rikkahub.utils.fileSizeToString
import me.rerere.rikkahub.utils.length
import me.rerere.rikkahub.utils.listFiles
import me.rerere.rikkahub.utils.mkdirs

private const val TAG = "WebDavSync"

class WebDavSync(
    private val settingsStore: SettingsStore,
    private val json: Json,
    private val layout: BackupFileLayout,
    private val httpClient: HttpClient,
) {
    private fun getClient(config: WebDavConfig): WebDavClient {
        return WebDavClient(config, httpClient)
    }

    suspend fun testConnection(config: WebDavConfig) = withContext(Dispatchers.IO) {
        val client = getClient(config)
        // Test by listing the root directory
        client.propfind(depth = 0).getOrThrow()
        Log.i(TAG, "testConnection: Connection successful")
    }

    suspend fun backup(config: WebDavConfig) = withContext(Dispatchers.IO) {
        val file = prepareBackupFile(config).toKotlinxIoPath()
        val client = getClient(config)

        // Ensure the backup directory exists
        client.ensureCollectionExists().getOrThrow()

        // Upload the backup file
        client.put(
            path = file.name,
            contentLength = file.length(),
            content = { ByteReadChannel(SystemFileSystem.source(file).buffered()) },
            contentType = "application/zip"
        ).getOrThrow()

        Log.i(TAG, "backup: Uploaded ${file.name} (${file.length().fileSizeToString()})")

        // Clean up temp file
        file.delete()
    }

    suspend fun listBackupFiles(config: WebDavConfig): List<WebDavBackupItem> = withContext(Dispatchers.IO) {
        val client = getClient(config)

        // Ensure the backup directory exists
        client.ensureCollectionExists().getOrThrow()

        val resources = client.list().getOrThrow()

        resources
            .filter { !it.isCollection && it.displayName.startsWith("backup_") && it.displayName.endsWith(".zip") }
            .map { resource ->
                WebDavBackupItem(
                    href = resource.href,
                    displayName = resource.displayName,
                    size = resource.contentLength,
                    lastModified = resource.lastModified ?: Instant.fromEpochMilliseconds(0)
                )
            }
            .sortedByDescending { it.lastModified }
    }

    suspend fun restore(config: WebDavConfig, item: WebDavBackupItem) = withContext(Dispatchers.IO) {
        val client = getClient(config)
        val backupFile = Path(layout.cacheRoot.toKotlinxIoPath(), item.displayName)

        try {
            // Download backup file directly to file to avoid OOM
            Log.i(TAG, "restore: Downloading ${item.displayName}")
            SystemFileSystem.sink(backupFile).buffered().use { output ->
                client.download(item.displayName) { buffer, byteCount ->
                    output.write(buffer, 0, byteCount)
                }.getOrThrow()
            }

            Log.i(TAG, "restore: Downloaded ${backupFile.length().fileSizeToString()}")

            // Restore from backup file
            restoreFromBackupFile(backupFile, config)
        } finally {
            // Clean up temp file
            if (backupFile.exists()) {
                backupFile.delete()
                Log.i(TAG, "restore: Cleaned up temporary backup file")
            }
        }
    }

    suspend fun deleteBackupFile(config: WebDavConfig, item: WebDavBackupItem) = withContext(Dispatchers.IO) {
        val client = getClient(config)
        client.delete(item.displayName).getOrThrow()
        Log.i(TAG, "deleteBackupFile: Deleted ${item.displayName}")
    }

    suspend fun restoreFromLocalFile(file: PlatformFile, config: WebDavConfig) {
        withContext(Dispatchers.IO) {
            val temporary = Path(layout.cacheRoot.toKotlinxIoPath(), "restore_${Uuid.random()}.zip")
            try {
                file.source().use { input ->
                    SystemFileSystem.sink(temporary).use { output ->
                        val buffer = Buffer()
                        while (true) {
                            val count = input.readAtMostTo(buffer, 8_192L)
                            if (count == -1L) break
                            output.write(buffer, count)
                        }
                        output.flush()
                    }
                }
                restoreFromLocalFile(temporary, config)
            } finally {
                temporary.delete()
            }
        }
    }

    suspend fun restoreFromLocalFile(file: Path, config: WebDavConfig) = withContext(Dispatchers.IO) {
        Log.i(TAG, "restoreFromLocalFile: Starting restore from $file")

        if (!file.exists()) {
            throw Exception("Backup file does not exist")
        }

        if (!file.canRead()) {
            throw Exception("Cannot read backup file")
        }

        try {
            restoreFromBackupFile(file, config)
            Log.i(TAG, "restoreFromLocalFile: Restore completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "restoreFromLocalFile: Failed to restore from local file", e)
            throw Exception("Restore failed: ${e.message}")
        }
    }

    suspend fun prepareBackupFile(config: WebDavConfig): PlatformFile = withContext(Dispatchers.IO) {
        val timestamp = Clock.System.now().toCompactFileTimestamp()
        val backupFile = Path(layout.cacheRoot.toKotlinxIoPath(), "backup_$timestamp.zip")

        if (backupFile.exists()) {
            backupFile.delete()
        }

        // Create zip file and backup data
        PlatformZipArchive.create(SystemFileSystem.sink(backupFile).buffered()) {
            addVirtualFileToZip(
                zipOut = this,
                name = "settings.json",
                content = json.encodeToString(settingsStore.settingsFlow.value)
            )

            // Backup database files
            if (config.items.contains(WebDavConfig.BackupItem.DATABASE)) {
                val dbFile = layout.databaseFiles["rikka_hub.db"]?.toKotlinxIoPath()
                if (dbFile?.exists() == true) {
                    addFileToZip(this, dbFile, "rikka_hub.db")
                }

                val walFile = layout.databaseFiles["rikka_hub-wal"]?.toKotlinxIoPath()
                if (walFile?.exists() == true) {
                    addFileToZip(this, walFile, "rikka_hub-wal")
                }

                val shmFile = layout.databaseFiles["rikka_hub-shm"]?.toKotlinxIoPath()
                if (shmFile?.exists() == true) {
                    addFileToZip(this, shmFile, "rikka_hub-shm")
                }
            }

            // Backup app files
            if (config.items.contains(WebDavConfig.BackupItem.FILES)) {
                val uploadFolder = Path(layout.filesRoot.toKotlinxIoPath(), FileFolders.UPLOAD)
                val safeUploadFolder = BackupZipSourcePolicy.resolveDirectory(layout.filesRoot.toKotlinxIoPath(), uploadFolder)
                if (safeUploadFolder != null) {
                    Log.i(TAG, "prepareBackupFile: Backing up files from $safeUploadFolder")
                    safeUploadFolder.listFiles()?.forEach { file ->
                        BackupZipSourcePolicy.resolveRegularFile(safeUploadFolder, file)?.let { safeFile ->
                            addFileToZip(this, safeFile, "${FileFolders.UPLOAD}/${safeFile.name}")
                        }
                    }
                } else {
                    Log.w(TAG, "prepareBackupFile: Upload folder does not exist or is not a directory")
                }

                val skillsFolder = Path(layout.filesRoot.toKotlinxIoPath(), FileFolders.SKILLS)
                val safeSkillsFolder = BackupZipSourcePolicy.resolveDirectory(layout.filesRoot.toKotlinxIoPath(), skillsFolder)
                if (safeSkillsFolder != null) {
                    Log.i(TAG, "prepareBackupFile: Backing up skills from $safeSkillsFolder")
                    addDirectoryToZip(
                        zipOut = this,
                        rootDir = safeSkillsFolder,
                        currentDir = safeSkillsFolder,
                        entryPrefix = "${FileFolders.SKILLS}/"
                    )
                } else {
                    Log.w(TAG, "prepareBackupFile: Skills folder does not exist or is not a directory")
                }

                val fontsFolder = Path(layout.filesRoot.toKotlinxIoPath(), FileFolders.FONTS)
                val safeFontsFolder = BackupZipSourcePolicy.resolveDirectory(layout.filesRoot.toKotlinxIoPath(), fontsFolder)
                if (safeFontsFolder != null) {
                    Log.i(TAG, "prepareBackupFile: Backing up fonts from $safeFontsFolder")
                    safeFontsFolder.listFiles()?.forEach { file ->
                        BackupZipSourcePolicy.resolveRegularFile(safeFontsFolder, file)?.let { safeFile ->
                            addFileToZip(this, safeFile, "${FileFolders.FONTS}/${safeFile.name}")
                        }
                    }
                } else {
                    Log.w(TAG, "prepareBackupFile: Fonts folder does not exist or is not a directory")
                }

                // 兼容迁移期间已保存的附件；新文件仍使用原 upload 目录。
                for (folderName in listOf("platform-files/attachments", "platform-files/images")) {
                    val folder = Path(layout.filesRoot.toKotlinxIoPath(), folderName)
                    val safeFolder = BackupZipSourcePolicy.resolveDirectory(layout.filesRoot.toKotlinxIoPath(), folder)
                    safeFolder?.listFiles()?.forEach { file ->
                        BackupZipSourcePolicy.resolveRegularFile(safeFolder, file)?.let { safeFile ->
                            addFileToZip(this, safeFile, "$folderName/${safeFile.name}")
                        }
                    }
                }
            }
        }

        Log.i(
            TAG,
            "prepareBackupFile: Created backup file ${backupFile.name} (${backupFile.length().fileSizeToString()})"
        )
        PlatformFile(backupFile.toString())
    }

    private suspend fun restoreFromBackupFile(backupFile: Path, config: WebDavConfig) = withContext(Dispatchers.IO) {
        Log.i(TAG, "restoreFromBackupFile: Starting restore from $backupFile")

        PlatformZipArchive.read(SystemFileSystem.source(backupFile).buffered()) { zipEntry ->
            val entryPath = ZipEntryPathPolicy.normalizeOrNull(zipEntry.name)
                ?: throw IllegalArgumentException("Unsafe ZIP entry path")
            Log.i(TAG, "restoreFromBackupFile: Processing entry $entryPath")

            when (entryPath) {
                "settings.json" -> {
                    val settingsJson = zipEntry.readText()
                    Log.i(TAG, "restoreFromBackupFile: Restoring settings")
                    try {
                        val migratedJson = SettingsJsonMigrator.migrate(settingsJson)
                        val settings = json.decodeFromString<Settings>(migratedJson)
                        settingsStore.update(settings)
                        Log.i(TAG, "restoreFromBackupFile: Settings restored successfully")
                    } catch (e: Exception) {
                        Log.e(TAG, "restoreFromBackupFile: Failed to restore settings", e)
                        throw Exception("Failed to restore settings: ${e.message}")
                    }
                }

                "rikka_hub.db", "rikka_hub-wal", "rikka_hub-shm" -> {
                    if (config.items.contains(WebDavConfig.BackupItem.DATABASE)) {
                        val dbFile = layout.databaseFiles[entryPath]?.toKotlinxIoPath()

                        dbFile?.let { targetFile ->
                            Log.i(
                                TAG,
                                "restoreFromBackupFile: Restoring $entryPath to $targetFile"
                            )
                            targetFile.parent?.mkdirs()
                            zipEntry.copyTo(targetFile)
                            Log.i(
                                TAG,
                                "restoreFromBackupFile: Restored $entryPath (${targetFile.length()} bytes)"
                            )
                        }
                    }
                }

                else -> {
                    val restoreFiles = config.items.contains(WebDavConfig.BackupItem.FILES)
                    val uploadFileName = ZipEntryPathPolicy.directChildOfOrNull(
                        entryPath,
                        FileFolders.UPLOAD
                    )
                    val skillRelativePath = ZipEntryPathPolicy.relativeToRootOrNull(
                        entryPath,
                        FileFolders.SKILLS
                    )?.takeIf { it.isNotEmpty() }
                    val fontFileName = ZipEntryPathPolicy.directChildOfOrNull(
                        entryPath,
                        FileFolders.FONTS
                    )

                    if (restoreFiles && uploadFileName != null) {
                        val targetFile = BackupZipPathResolver.resolveDirectChild(
                            filesDir = layout.filesRoot.toKotlinxIoPath(),
                            folderName = FileFolders.UPLOAD,
                            entryPath = entryPath
                        ) ?: throw IllegalArgumentException("Unsafe upload ZIP entry path")
                        Log.i(
                            TAG,
                            "restoreFromBackupFile: Restoring file $entryPath to $targetFile"
                        )

                        try {
                            zipEntry.copyTo(targetFile)
                            Log.i(
                                TAG,
                                "restoreFromBackupFile: Restored $entryPath (${targetFile.length()} bytes)"
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "restoreFromBackupFile: Failed to restore file $entryPath", e)
                            throw Exception("Failed to restore file $entryPath: ${e.message}")
                        }
                    } else if (restoreFiles && skillRelativePath != null) {
                        restoreSkillEntry(zipEntry, entryPath)
                    } else if (restoreFiles && fontFileName != null) {
                        val targetFile = BackupZipPathResolver.resolveDirectChild(
                            filesDir = layout.filesRoot.toKotlinxIoPath(),
                            folderName = FileFolders.FONTS,
                            entryPath = entryPath
                        ) ?: throw IllegalArgumentException("Unsafe font ZIP entry path")
                        zipEntry.copyTo(targetFile)
                        Log.i(
                            TAG,
                            "restoreFromBackupFile: Restored $entryPath (${targetFile.length()} bytes)"
                        )
                    } else if (restoreFiles && (
                            ZipEntryPathPolicy.directChildOfOrNull(entryPath, "platform-files/attachments") != null ||
                                ZipEntryPathPolicy.directChildOfOrNull(entryPath, "platform-files/images") != null
                            )) {
                        val folderName = entryPath.substringBeforeLast('/')
                        val targetFile = BackupZipPathResolver.resolveDirectChild(
                            filesDir = layout.filesRoot.toKotlinxIoPath(),
                            folderName = folderName,
                            entryPath = entryPath
                        ) ?: throw IllegalArgumentException("Unsafe attachment ZIP entry path")
                        zipEntry.copyTo(targetFile)
                    } else {
                        Log.i(TAG, "restoreFromBackupFile: Skipping entry $entryPath")
                    }
                }
            }
        }

        Log.i(TAG, "restoreFromBackupFile: Restore completed successfully")
    }

    private fun addFileToZip(zipOut: ZipArchiveWriter, file: Path, entryName: String) {
        zipOut.add(entryName, SystemFileSystem.source(file).buffered())
        Log.d(TAG, "addFileToZip: Added $entryName (${file.length()} bytes) to zip")
    }

    private fun addDirectoryToZip(
        zipOut: ZipArchiveWriter,
        rootDir: Path,
        currentDir: Path,
        entryPrefix: String,
    ) {
        currentDir.listFiles()?.forEach { file ->
            val safeDirectory = BackupZipSourcePolicy.resolveDirectory(rootDir, file)
            val safeFile = BackupZipSourcePolicy.resolveRegularFile(rootDir, file)
            if (safeDirectory != null) {
                addDirectoryToZip(
                    zipOut = zipOut,
                    rootDir = rootDir,
                    currentDir = safeDirectory,
                    entryPrefix = entryPrefix,
                )
            } else if (safeFile != null) {
                val relativePath = safeFile.toString()
                    .removePrefix(rootDir.toString() + SystemPathSeparator)
                    .replace(SystemPathSeparator, '/')
                addFileToZip(zipOut, safeFile, "$entryPrefix$relativePath")
            }
        }
    }

    private fun restoreSkillEntry(zipEntry: ZipArchiveEntry, entryName: String) {
        val relativePath = ZipEntryPathPolicy.relativeToRootOrNull(entryName, FileFolders.SKILLS)
            ?: throw Exception("Invalid skill entry: $entryName")
        val skillName = relativePath.substringBefore('/', missingDelimiterValue = "")
        val skillRelativePath = relativePath.substringAfter('/', missingDelimiterValue = "")

        if (skillName.isBlank() || skillRelativePath.isBlank()) {
            Log.w(TAG, "restoreFromBackupFile: Invalid skill entry $entryName")
            return
        }

        val skillsRoot = Path(layout.filesRoot.toKotlinxIoPath(), FileFolders.SKILLS).apply { mkdirs() }
        val skillDir = SkillPaths.resolveSkillDir(skillsRoot, skillName)
            ?: throw Exception("Invalid skill directory: $entryName")
        val targetFile = SkillPaths.resolveSkillFile(skillDir, skillRelativePath)
            ?: throw Exception("Invalid skill file path: $entryName")

        skillDir.mkdirs()
        targetFile.parent?.mkdirs()

        try {
            zipEntry.copyTo(targetFile)
            Log.i(TAG, "restoreFromBackupFile: Restored skill file $entryName (${targetFile.length()} bytes)")
        } catch (e: Exception) {
            Log.e(TAG, "restoreFromBackupFile: Failed to restore skill file $entryName", e)
            throw Exception("Failed to restore skill file $entryName: ${e.message}")
        }
    }

    private fun addVirtualFileToZip(zipOut: ZipArchiveWriter, name: String, content: String) {
        zipOut.addText(name, content)
        Log.i(TAG, "addVirtualFileToZip: $name (${content.length} bytes)")
    }

    private fun ZipArchiveEntry.copyTo(file: Path) {
        SystemFileSystem.sink(file).buffered().use { sink ->
            copyTo(sink)
        }
    }
}

data class WebDavBackupItem(
    val href: String,
    val displayName: String,
    val size: Long,
    val lastModified: Instant,
)
