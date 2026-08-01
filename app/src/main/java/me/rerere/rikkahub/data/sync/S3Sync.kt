package me.rerere.rikkahub.data.sync

import android.content.Context
import me.rerere.common.logging.RikkaLog as Log
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.Json
import me.rerere.common.archive.PlatformZipArchive
import me.rerere.common.archive.ZipArchiveEntry
import me.rerere.common.archive.ZipArchiveWriter
import me.rerere.common.archive.ZipEntryPathPolicy
import me.rerere.common.archive.addText
import me.rerere.common.archive.readText
import me.rerere.common.time.toCompactFileTimestamp
import me.rerere.rikkahub.data.files.FileFolders
import me.rerere.rikkahub.data.files.SkillPaths
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.migration.SettingsJsonMigrator
import me.rerere.rikkahub.data.sync.s3.S3Client
import me.rerere.rikkahub.data.sync.s3.S3Config
import me.rerere.rikkahub.utils.fileSizeToString
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.time.Clock
import kotlin.time.Instant

private const val TAG = "S3Sync"

class S3Sync(
    private val settingsStore: SettingsStore,
    private val json: Json,
    private val context: Context,
    private val httpClient: HttpClient,
) {
    private fun getS3Client(config: S3Config): S3Client {
        return S3Client(config, httpClient)
    }

    suspend fun testS3(config: S3Config) = withContext(Dispatchers.IO) {
        val client = getS3Client(config)
        // Test by listing objects with max 1 result
        client.listObjects(maxKeys = 1).getOrThrow()
        Log.i(TAG, "testS3: Connection successful")
    }

    suspend fun backupToS3(config: S3Config) = withContext(Dispatchers.IO) {
        val file = prepareBackupFile(config)
        val client = getS3Client(config)
        val key = "rikkahub_backups/${file.name}"

        client.putObject(
            key = key,
            file = file,
            contentType = "application/zip"
        ).getOrThrow()

        Log.i(TAG, "backupToS3: Uploaded ${file.name} (${file.length().fileSizeToString()})")

        // Clean up temp file
        file.delete()
    }

    suspend fun listBackupFiles(config: S3Config): List<S3BackupItem> = withContext(Dispatchers.IO) {
        val client = getS3Client(config)
        val result = client.listObjects(
            prefix = "rikkahub_backups/",
            maxKeys = 1000
        ).getOrThrow()

        result.objects
            .filter { it.key.startsWith("rikkahub_backups/backup_") && it.key.endsWith(".zip") }
            .map { obj ->
                S3BackupItem(
                    key = obj.key,
                    displayName = obj.key.substringAfterLast("/"),
                    size = obj.size,
                    lastModified = obj.lastModified ?: Instant.fromEpochMilliseconds(0)
                )
            }
            .sortedByDescending { it.lastModified }
    }

    suspend fun restoreFromS3(config: S3Config, item: S3BackupItem) = withContext(Dispatchers.IO) {
        val client = getS3Client(config)
        val backupFile = File(context.cacheDir, item.displayName)

        try {
            // Download backup file directly to file to avoid OOM
            Log.i(TAG, "restoreFromS3: Downloading ${item.displayName}")
            client.downloadObjectToFile(item.key, backupFile).getOrThrow()

            Log.i(TAG, "restoreFromS3: Downloaded ${backupFile.length().fileSizeToString()}")

            // Restore from backup file
            restoreFromBackupFile(backupFile, config)
        } finally {
            // Clean up temp file
            if (backupFile.exists()) {
                backupFile.delete()
                Log.i(TAG, "restoreFromS3: Cleaned up temporary backup file")
            }
        }
    }

    suspend fun deleteS3BackupFile(config: S3Config, item: S3BackupItem) = withContext(Dispatchers.IO) {
        val client = getS3Client(config)
        client.deleteObject(item.key).getOrThrow()
        Log.i(TAG, "deleteS3BackupFile: Deleted ${item.key}")
    }

    suspend fun prepareBackupFile(config: S3Config): File = withContext(Dispatchers.IO) {
        val timestamp = Clock.System.now().toCompactFileTimestamp()
        val backupFile = File(context.cacheDir, "backup_$timestamp.zip")

        if (backupFile.exists()) {
            backupFile.delete()
        }

        // Create zip file and backup data
        PlatformZipArchive.create(FileOutputStream(backupFile).asSink().buffered()) {
            addVirtualFileToZip(
                zipOut = this,
                name = "settings.json",
                content = json.encodeToString(settingsStore.settingsFlow.value)
            )

            // Backup database files
            if (config.items.contains(S3Config.BackupItem.DATABASE)) {
                val dbFile = context.getDatabasePath("rikka_hub")
                if (dbFile.exists()) {
                    addFileToZip(this, dbFile, "rikka_hub.db")
                }

                val walFile = File(dbFile.parentFile, "rikka_hub-wal")
                if (walFile.exists()) {
                    addFileToZip(this, walFile, "rikka_hub-wal")
                }

                val shmFile = File(dbFile.parentFile, "rikka_hub-shm")
                if (shmFile.exists()) {
                    addFileToZip(this, shmFile, "rikka_hub-shm")
                }
            }

            // Backup app files
            if (config.items.contains(S3Config.BackupItem.FILES)) {
                val uploadFolder = File(context.filesDir, FileFolders.UPLOAD)
                val safeUploadFolder = BackupZipSourcePolicy.resolveDirectory(context.filesDir, uploadFolder)
                if (safeUploadFolder != null) {
                    Log.i(TAG, "prepareBackupFile: Backing up files from ${safeUploadFolder.absolutePath}")
                    safeUploadFolder.listFiles()?.forEach { file ->
                        BackupZipSourcePolicy.resolveRegularFile(safeUploadFolder, file)?.let { safeFile ->
                            addFileToZip(this, safeFile, "${FileFolders.UPLOAD}/${safeFile.name}")
                        }
                    }
                } else {
                    Log.w(TAG, "prepareBackupFile: Upload folder does not exist or is not a directory")
                }

                val skillsFolder = File(context.filesDir, FileFolders.SKILLS)
                val safeSkillsFolder = BackupZipSourcePolicy.resolveDirectory(context.filesDir, skillsFolder)
                if (safeSkillsFolder != null) {
                    Log.i(TAG, "prepareBackupFile: Backing up skills from ${safeSkillsFolder.absolutePath}")
                    addDirectoryToZip(
                        zipOut = this,
                        rootDir = safeSkillsFolder,
                        currentDir = safeSkillsFolder,
                        entryPrefix = "${FileFolders.SKILLS}/"
                    )
                } else {
                    Log.w(TAG, "prepareBackupFile: Skills folder does not exist or is not a directory")
                }

                val fontsFolder = File(context.filesDir, FileFolders.FONTS)
                val safeFontsFolder = BackupZipSourcePolicy.resolveDirectory(context.filesDir, fontsFolder)
                if (safeFontsFolder != null) {
                    Log.i(TAG, "prepareBackupFile: Backing up fonts from ${safeFontsFolder.absolutePath}")
                    safeFontsFolder.listFiles()?.forEach { file ->
                        BackupZipSourcePolicy.resolveRegularFile(safeFontsFolder, file)?.let { safeFile ->
                            addFileToZip(this, safeFile, "${FileFolders.FONTS}/${safeFile.name}")
                        }
                    }
                } else {
                    Log.w(TAG, "prepareBackupFile: Fonts folder does not exist or is not a directory")
                }
            }
        }

        Log.i(
            TAG,
            "prepareBackupFile: Created backup file ${backupFile.name} (${backupFile.length().fileSizeToString()})"
        )
        backupFile
    }

    private suspend fun restoreFromBackupFile(backupFile: File, config: S3Config) = withContext(Dispatchers.IO) {
        Log.i(TAG, "restoreFromBackupFile: Starting restore from ${backupFile.absolutePath}")

        PlatformZipArchive.read(FileInputStream(backupFile).asSource().buffered()) { zipEntry ->
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
                    if (config.items.contains(S3Config.BackupItem.DATABASE)) {
                        val dbFile = when (entryPath) {
                            "rikka_hub.db" -> context.getDatabasePath("rikka_hub")
                            "rikka_hub-wal" -> File(
                                context.getDatabasePath("rikka_hub").parentFile,
                                "rikka_hub-wal"
                            )

                            "rikka_hub-shm" -> File(
                                context.getDatabasePath("rikka_hub").parentFile,
                                "rikka_hub-shm"
                            )

                            else -> null
                        }

                        dbFile?.let { targetFile ->
                            Log.i(
                                TAG,
                                "restoreFromBackupFile: Restoring $entryPath to ${targetFile.absolutePath}"
                            )
                            targetFile.parentFile?.mkdirs()
                            zipEntry.copyTo(targetFile)
                            Log.i(
                                TAG,
                                "restoreFromBackupFile: Restored $entryPath (${targetFile.length()} bytes)"
                            )
                        }
                    }
                }

                else -> {
                    val restoreFiles = config.items.contains(S3Config.BackupItem.FILES)
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
                            filesDir = context.filesDir,
                            folderName = FileFolders.UPLOAD,
                            entryPath = entryPath
                        ) ?: throw IllegalArgumentException("Unsafe upload ZIP entry path")
                        Log.i(
                            TAG,
                            "restoreFromBackupFile: Restoring file $entryPath to ${targetFile.absolutePath}"
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
                            filesDir = context.filesDir,
                            folderName = FileFolders.FONTS,
                            entryPath = entryPath
                        ) ?: throw IllegalArgumentException("Unsafe font ZIP entry path")
                        zipEntry.copyTo(targetFile)
                        Log.i(
                            TAG,
                            "restoreFromBackupFile: Restored $entryPath (${targetFile.length()} bytes)"
                        )
                    } else {
                        Log.i(TAG, "restoreFromBackupFile: Skipping entry $entryPath")
                    }
                }
            }
        }

        Log.i(TAG, "restoreFromBackupFile: Restore completed successfully")
    }

    private fun addFileToZip(zipOut: ZipArchiveWriter, file: File, entryName: String) {
        zipOut.add(entryName, FileInputStream(file).asSource().buffered())
        Log.d(TAG, "addFileToZip: Added $entryName (${file.length()} bytes) to zip")
    }

    private fun addDirectoryToZip(
        zipOut: ZipArchiveWriter,
        rootDir: File,
        currentDir: File,
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
                val relativePath = safeFile.relativeTo(rootDir).invariantSeparatorsPath
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

        val skillsRoot = File(context.filesDir, FileFolders.SKILLS).apply { mkdirs() }
        val skillDir = SkillPaths.resolveSkillDir(skillsRoot, skillName)
            ?: throw Exception("Invalid skill directory: $entryName")
        val targetFile = SkillPaths.resolveSkillFile(skillDir, skillRelativePath)
            ?: throw Exception("Invalid skill file path: $entryName")

        skillDir.mkdirs()
        targetFile.parentFile?.mkdirs()

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

    private fun ZipArchiveEntry.copyTo(file: File) {
        FileOutputStream(file).asSink().buffered().use { sink ->
            copyTo(sink)
        }
    }
}

data class S3BackupItem(
    val key: String,
    val displayName: String,
    val size: Long,
    val lastModified: Instant,
)
