package me.rerere.rikkahub.data.sync

import co.touchlab.kermit.Logger
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.cacheDir
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.databasesDir
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.filesDir
import io.github.vinceglb.filekit.isDirectory
import io.github.vinceglb.filekit.isRegularFile
import io.github.vinceglb.filekit.list
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.parent
import io.github.vinceglb.filekit.resolve
import io.github.vinceglb.filekit.size
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import me.rerere.common.PlatformContext
import me.rerere.common.utils.delete
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.sync.s3.S3Client
import me.rerere.rikkahub.data.sync.s3.S3Config
import me.rerere.rikkahub.utils.ZipUtil
import me.rerere.rikkahub.utils.fileSizeToString
import kotlin.time.Clock
import kotlin.time.Instant

private const val TAG = "S3Sync"

class S3Sync(
    private val settingsStore: SettingsStore,
    private val json: Json,
    private val context: PlatformContext,
    private val httpClient: HttpClient,
    private val zipUtil: ZipUtil,
) {
    private fun getS3Client(config: S3Config): S3Client {
        return S3Client(config, httpClient)
    }

    suspend fun testS3(config: S3Config) = withContext(Dispatchers.IO) {
        val client = getS3Client(config)
        // Test by listing objects with max 1 result
        client.listObjects(maxKeys = 1).getOrThrow()
        Logger.i(TAG) { "testS3: Connection successful" }
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

        Logger.i(TAG) { "backupToS3: Uploaded ${file.name} (${file.size().fileSizeToString()})" }

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
                    lastModified = obj.lastModified ?: Instant.fromEpochSeconds(0)
                )
            }
            .sortedByDescending { it.lastModified }
    }

    suspend fun restoreFromS3(config: S3Config, item: S3BackupItem) = withContext(Dispatchers.IO) {
        val client = getS3Client(config)
        val backupFile = PlatformFile(FileKit.cacheDir, item.displayName)

        try {
            // Download backup file directly to file to avoid OOM
            Logger.i(TAG) { "restoreFromS3: Downloading ${item.displayName}" }
            client.downloadObjectToFile(item.key, backupFile).getOrThrow()

            Logger.i(TAG) { "restoreFromS3: Downloaded ${backupFile.size().fileSizeToString()}" }

            // Restore from backup file
            restoreFromBackupFile(backupFile, config)
        } finally {
            // Clean up temp file
            if (backupFile.exists()) {
                backupFile.delete()
                Logger.i(TAG) { "restoreFromS3: Cleaned up temporary backup file" }
            }
        }
    }

    suspend fun deleteS3BackupFile(config: S3Config, item: S3BackupItem) = withContext(Dispatchers.IO) {
        val client = getS3Client(config)
        client.deleteObject(item.key).getOrThrow()
        Logger.i(TAG) { "deleteS3BackupFile: Deleted ${item.key}" }
    }

    // ---- 时间格式化（替代 Java DateTimeFormatter）----
    private fun formatTimestamp(): String {
        val now = Clock.System.now()
        val local = now.toLocalDateTime(TimeZone.currentSystemDefault())
        return LocalDateTime.Format {
            year()
            monthNumber()
            day()
            char('_')
            hour()
            minute()
            second()
        }.format(local)
    }

    suspend fun prepareBackupFile(config: S3Config): PlatformFile = withContext(Dispatchers.IO) {
        val timestamp = formatTimestamp()
        val backupFile = PlatformFile(FileKit.cacheDir, "backup_$timestamp.zip")
        if (backupFile.exists()) {
            backupFile.delete()
        }

        val writer = zipUtil.createZipWriter(backupFile.absolutePath())

        writer.use { zip ->
            // 1. settings.json（小数据，内存写入）
            val settingsContent = json.encodeToString(settingsStore.settingsFlow.value)
            zip.addEntry("settings.json", settingsContent.encodeToByteArray())
            Logger.i(TAG) { "addVirtualFileToZip: settings.json (${settingsContent.length} bytes)" }
            // 2. 数据库文件（流式从文件添加）
            if (config.items.contains(S3Config.BackupItem.DATABASE)) {
                val dbFile = FileKit.databasesDir.resolve("rikka_hub")
                if (dbFile.exists()) {
                    zip.addEntryFromFile("rikka_hub.db", dbFile.absolutePath())
                    Logger.d(TAG) { "addFileToZip: Added rikka_hub.db (${dbFile.size()} bytes) to zip" }
                }
                val dbParent = dbFile.parent()
                if (dbParent != null) {
                    val walFile = PlatformFile(dbParent, "rikka_hub-wal")
                    if (walFile.exists()) {
                        zip.addEntryFromFile("rikka_hub-wal", walFile.absolutePath())
                        Logger.d(TAG) { "addFileToZip: Added rikka_hub-wal (${walFile.size()} bytes) to zip" }
                    }
                    val shmFile = PlatformFile(dbParent, "rikka_hub-shm")
                    if (shmFile.exists()) {
                        zip.addEntryFromFile("rikka_hub-shm", shmFile.absolutePath())
                        Logger.d(TAG) { "addFileToZip: Added rikka_hub-shm (${shmFile.size()} bytes) to zip" }
                    }
                }
            }
            // 3. upload 文件夹（流式逐文件添加）
            if (config.items.contains(S3Config.BackupItem.FILES)) {
                val uploadFolder = PlatformFile(FileKit.filesDir, "upload")
                if (uploadFolder.exists() && uploadFolder.isDirectory()) {
                    Logger.i(TAG) { "prepareBackupFile: Backing up files from ${uploadFolder.absolutePath()}" }
                    uploadFolder.list().forEach { file ->
                        if (file.isRegularFile()) {
                            zip.addEntryFromFile("upload/${file.name}", file.absolutePath())
                            Logger.d(TAG) { "addFileToZip: Added upload/${file.name} (${file.size()} bytes) to zip" }
                        }
                    }
                } else {
                    Logger.w(TAG) { "prepareBackupFile: Upload folder does not exist or is not a directory" }
                }
            }
        }

        Logger.i(TAG) {
            "prepareBackupFile: Created backup file ${backupFile.name} (${backupFile.size().fileSizeToString()})"
        }
        backupFile
    }

    private suspend fun restoreFromBackupFile(backupFile: PlatformFile, config: S3Config) =
        withContext(Dispatchers.IO) {
            Logger.i(TAG) { "restoreFromBackupFile: Starting restore from ${backupFile.absolutePath()}" }
            val zipPath = backupFile.absolutePath()
            val entries = zipUtil.getZipEntryList(zipPath)
            for ((entryName, isDir) in entries) {
                if (isDir) continue
                Logger.i(TAG) { "restoreFromBackupFile: Processing entry $entryName" }
                when (entryName) {
                    "settings.json" -> {
                        // settings 很小，直接读到内存解析
                        val content = zipUtil.getZipEntryContent(zipPath, entryName)
                            ?: throw Exception("Failed to read settings.json from backup")
                        val settingsJson = content.decodeToString()
                        Logger.i(TAG) { "restoreFromBackupFile: Restoring settings" }
                        try {
                            val settings = json.decodeFromString<Settings>(settingsJson)
                            settingsStore.update(settings)
                            Logger.i(TAG) { "restoreFromBackupFile: Settings restored successfully" }
                        } catch (e: Exception) {
                            Logger.e(TAG, e) { "restoreFromBackupFile: Failed to restore settings" }
                            throw Exception("Failed to restore settings: ${e.message}")
                        }
                    }
                    "rikka_hub.db", "rikka_hub-wal", "rikka_hub-shm" -> {
                        if (config.items.contains(S3Config.BackupItem.DATABASE)) {
                            val dbBase = FileKit.databasesDir.resolve("rikka_hub")
                            val dbParent = dbBase.parent()
                            val targetFile: PlatformFile? = when (entryName) {
                                "rikka_hub.db" -> dbBase
                                "rikka_hub-wal" -> dbParent?.let { PlatformFile(it, "rikka_hub-wal") }
                                "rikka_hub-shm" -> dbParent?.let { PlatformFile(it, "rikka_hub-shm") }
                                else -> null
                            }
                            targetFile?.let { target ->
                                Logger.i(TAG) { "restoreFromBackupFile: Restoring $entryName to ${target.absolutePath()}" }
                                target.parent()?.createDirectories()
                                // 流式提取到文件
                                val success = zipUtil.extractEntryToFile(zipPath, entryName, target.absolutePath())
                                if (!success) {
                                    throw Exception("Failed to extract $entryName from backup")
                                }
                                Logger.i(TAG) { "restoreFromBackupFile: Restored $entryName (${target.size()} bytes)" }
                            }
                        }
                    }
                    else -> {
                        if (config.items.contains(S3Config.BackupItem.FILES) && entryName.startsWith("upload/")) {
                            val fileName = entryName.substringAfter("upload/")
                            if (fileName.isNotEmpty()) {
                                val uploadFolder = PlatformFile(FileKit.filesDir, "upload")
                                if (!uploadFolder.exists()) {
                                    uploadFolder.createDirectories()
                                    Logger.i(TAG) { "restoreFromBackupFile: Created upload directory" }
                                }
                                val targetFile = PlatformFile(uploadFolder, fileName)
                                Logger.i(TAG) { "restoreFromBackupFile: Restoring file $entryName to ${targetFile.absolutePath()}" }
                                try {
                                    // 流式提取到文件
                                    val success = zipUtil.extractEntryToFile(zipPath, entryName, targetFile.absolutePath())
                                    if (!success) {
                                        throw Exception("Failed to extract $entryName from backup")
                                    }
                                    Logger.i(TAG) { "restoreFromBackupFile: Restored $entryName (${targetFile.size()} bytes)" }
                                } catch (e: Exception) {
                                    Logger.e(TAG, e) { "restoreFromBackupFile: Failed to restore file $entryName" }
                                    throw Exception("Failed to restore file $entryName: ${e.message}")
                                }
                            }
                        } else {
                            Logger.i(TAG) { "restoreFromBackupFile: Skipping entry $entryName" }
                        }
                    }
                }
            }
            Logger.i(TAG) { "restoreFromBackupFile: Restore completed successfully" }
        }
}

data class S3BackupItem(
    val key: String,
    val displayName: String,
    val size: Long,
    val lastModified: Instant,
)
