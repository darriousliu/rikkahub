package me.rerere.rikkahub.data.sync

import android.content.Context
import android.util.Log
import io.github.triangleofice.dav4kmp.DavCollection
import io.github.triangleofice.dav4kmp.Response
import io.github.triangleofice.dav4kmp.exception.NotFoundException
import io.github.triangleofice.dav4kmp.property.DisplayName
import io.github.triangleofice.dav4kmp.property.GetContentLength
import io.github.triangleofice.dav4kmp.property.GetLastModified
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRedirect
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.DigestAuthCredentials
import io.ktor.client.plugins.auth.providers.digest
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentType
import io.ktor.http.Url
import io.ktor.http.defaultForFileExtension
import io.ktor.http.isSuccess
import io.ktor.util.cio.readChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.WebDavConfig
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private const val TAG = "DataSync"

class DataSync(
    private val settingsStore: SettingsStore,
    private val json: Json,
    private val context: Context,
) {
    suspend fun testWebdav(webDavConfig: WebDavConfig) {
        val davCollection = DavCollection(
            httpClient = webDavConfig.requireClient(),
            location = Url(webDavConfig.url),
        )

        withContext(Dispatchers.IO) {
            davCollection.propfind(
                depth = 1,
                DisplayName.NAME,
            ) { response, relation ->
                Log.i(TAG, "testWebdav: $response | $relation")
            }
        }
    }

    suspend fun backupToWebDav(webDavConfig: WebDavConfig) = withContext(Dispatchers.IO) {
        val file = prepareBackupFile(webDavConfig)
        val collection = webDavConfig.requireCollection()
        collection.ensureCollectionExists() // ensure collection exists
        val target = webDavConfig.requireCollection(file.name)
        val body = file.readChannel()
        target.put(
            body = body,
            contentType = ContentType.defaultForFileExtension(file.extension)
        ) { response ->
            Log.i(TAG, "backupToWebDav: $response")
        }
    }

    suspend fun listBackupFiles(webDavConfig: WebDavConfig): List<BackupFileItem> =
        withContext(Dispatchers.IO) {
            val collection = webDavConfig.requireCollection()
            val files = mutableListOf<BackupFileItem>()
            collection.propfind(
                depth = 1,
                DisplayName.NAME,
                GetContentLength.NAME,
                GetLastModified.NAME
            ) { response, relation ->
                Log.i(TAG, "listBackupFiles: ${response.properties} ${response.href}")
                if (relation == Response.HrefRelation.MEMBER) {
                    val displayName = response.properties.filterIsInstance<DisplayName>()
                        .firstOrNull()?.displayName ?: "Unknown"
                    val size = response.properties.filterIsInstance<GetContentLength>()
                        .firstOrNull()?.contentLength ?: 0L
                    val lastModified = Instant.fromEpochMilliseconds(
                        response.properties.filterIsInstance<GetLastModified>()
                            .firstOrNull()?.lastModified ?: 0L
                    )
                    files.add(
                        BackupFileItem(
                            href = response.href.toString(),
                            displayName = displayName,
                            size = size,
                            lastModified = lastModified
                        )
                    )
                }
            }
            files
        }

    suspend fun restoreFromWebDav(webDavConfig: WebDavConfig, item: BackupFileItem) =
        withContext(Dispatchers.IO) {
            val collection = DavCollection(
                httpClient = webDavConfig.requireClient(),
                location = Url(item.href),
            )
            val backupFile = File(context.cacheDir, item.displayName)
            if (backupFile.exists()) {
                backupFile.delete()
            }

            // 下载备份文件
            collection.get(
                accept = "",
                headers = null
            ) { response ->
                if (response.status.isSuccess()) {
                    Log.i(
                        TAG,
                        "restoreFromWebDav: Downloading ${item.displayName} to ${backupFile.absolutePath}"
                    )
                    response.bodyAsBytes().let { inputStream ->
                        FileOutputStream(backupFile).use { outputStream ->
                            outputStream.write(inputStream)
                        }
                    }
                } else {
                    Log.e(
                        TAG,
                        "restoreFromWebDav: Failed to download ${item.displayName}, response: $response"
                    )
                    throw Exception("Failed to download backup file: ${response.status.description}")
                }
            }

            Log.i(TAG, "restoreFromWebDav: Downloaded ${backupFile.length()} bytes")

            try {
                // 解压并恢复备份文件
                restoreFromBackupFile(backupFile, webDavConfig)
            } finally {
                // 清理临时文件
                if (backupFile.exists()) {
                    backupFile.delete()
                    Log.i(TAG, "restoreFromWebDav: Cleaned up temporary backup file")
                }
            }
        }

    suspend fun deleteWebDavBackupFile(webDavConfig: WebDavConfig, item: BackupFileItem) =
        withContext(Dispatchers.IO) {
            val collection = DavCollection(
                httpClient = webDavConfig.requireClient(),
                location = Url(item.href)
            )
            collection.delete { response ->
                Log.i(TAG, "deleteWebDavBackupFile: $response")
            }
        }

    suspend fun restoreFromLocalFile(file: File, webDavConfig: WebDavConfig) =
        withContext(Dispatchers.IO) {
            Log.i(TAG, "restoreFromLocalFile: Starting restore from ${file.absolutePath}")

            if (!file.exists()) {
                throw Exception("备份文件不存在")
            }

            if (!file.canRead()) {
                throw Exception("无法读取备份文件")
            }

            try {
                restoreFromBackupFile(file, webDavConfig)
                Log.i(TAG, "restoreFromLocalFile: Restore completed successfully")
            } catch (e: Exception) {
                Log.e(TAG, "restoreFromLocalFile: Failed to restore from local file", e)
                throw Exception("恢复失败: ${e.message}")
            }
        }

    suspend fun prepareBackupFile(webDavConfig: WebDavConfig): File = withContext(Dispatchers.IO) {
        val timestamp = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        //DateTimeFormat.ofPattern("yyyyMMdd_HHmmss")
        timestamp.format(
            LocalDateTime.Format {
                year()
                monthNumber()
                day()
                char('_')
                hour()
                minute()
                second()
            }
        )
        val backupFile = File(
            context.cacheDir,
            "backup_$timestamp.zip"
        )
        if (backupFile.exists()) {
            backupFile.delete()
        }

        // 创建zip文件并备份数据库
        ZipOutputStream(FileOutputStream(backupFile)).use { zipOut ->
            addVirtualFileToZip(
                zipOut = zipOut,
                name = "settings.json",
                content = json.encodeToString(settingsStore.settingsFlow.value)
            )

            // 备份数据库
            if (webDavConfig.items.contains(WebDavConfig.BackupItem.DATABASE)) {
                // 备份主数据库文件
                val dbFile = context.getDatabasePath("rikka_hub")
                if (dbFile.exists()) {
                    addFileToZip(zipOut, dbFile, "rikka_hub.db")
                }

                // 备份数据库的WAL文件（如果存在）
                val walFile = File(dbFile.parentFile, "rikka_hub-wal")
                if (walFile.exists()) {
                    addFileToZip(zipOut, walFile, "rikka_hub-wal")
                }

                // 备份数据库的SHM文件（如果存在）
                val shmFile = File(dbFile.parentFile, "rikka_hub-shm")
                if (shmFile.exists()) {
                    addFileToZip(zipOut, shmFile, "rikka_hub-shm")
                }
            }

            // 备份聊天文件
            if (webDavConfig.items.contains(WebDavConfig.BackupItem.FILES)) {
                val uploadFolder = File(context.filesDir, "upload")
                if (uploadFolder.exists() && uploadFolder.isDirectory) {
                    Log.i(
                        TAG,
                        "prepareBackupFile: Backing up files from ${uploadFolder.absolutePath}"
                    )
                    uploadFolder.listFiles()?.forEach { file ->
                        if (file.isFile) {
                            addFileToZip(zipOut, file, "upload/${file.name}")
                        }
                    }
                } else {
                    Log.w(
                        TAG,
                        "prepareBackupFile: Upload folder does not exist or is not a directory"
                    )
                }
            }
        }

        backupFile
    }

    private suspend fun restoreFromBackupFile(backupFile: File, webDavConfig: WebDavConfig) =
        withContext(Dispatchers.IO) {
            Log.i(TAG, "restoreFromBackupFile: Starting restore from ${backupFile.absolutePath}")

            ZipInputStream(FileInputStream(backupFile)).use { zipIn ->
                var entry: ZipEntry?
                while (zipIn.nextEntry.also { entry = it } != null) {
                    entry?.let { zipEntry ->
                        Log.i(TAG, "restoreFromBackupFile: Processing entry ${zipEntry.name}")

                        when (zipEntry.name) {
                            "settings.json" -> {
                                // 恢复设置
                                val settingsJson = zipIn.readBytes().toString(Charsets.UTF_8)
                                Log.i(TAG, "restoreFromBackupFile: Restoring settings")
                                try {
                                    val settings = json.decodeFromString<Settings>(settingsJson)
                                    settingsStore.update(settings)
                                    Log.i(
                                        TAG,
                                        "restoreFromBackupFile: Settings restored successfully"
                                    )
                                } catch (e: Exception) {
                                    Log.e(
                                        TAG,
                                        "restoreFromBackupFile: Failed to restore settings",
                                        e
                                    )
                                    throw Exception("Failed to restore settings: ${e.message}")
                                }
                            }

                            "rikka_hub.db", "rikka_hub-wal", "rikka_hub-shm" -> {
                                if (webDavConfig.items.contains(WebDavConfig.BackupItem.DATABASE)) {
                                    // 恢复数据库文件
                                    val dbFile = when (zipEntry.name) {
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
                                            "restoreFromBackupFile: Restoring ${zipEntry.name} to ${targetFile.absolutePath}"
                                        )

                                        // 确保父目录存在
                                        targetFile.parentFile?.mkdirs()

                                        // 写入文件
                                        FileOutputStream(targetFile).use { outputStream ->
                                            zipIn.copyTo(outputStream)
                                        }

                                        Log.i(
                                            TAG,
                                            "restoreFromBackupFile: Restored ${zipEntry.name} (${targetFile.length()} bytes)"
                                        )
                                    }
                                }
                            }

                            else -> {
                                // 处理聊天文件
                                if (webDavConfig.items.contains(WebDavConfig.BackupItem.FILES) && zipEntry.name.startsWith(
                                        "upload/"
                                    )
                                ) {
                                    val fileName = zipEntry.name.substringAfter("upload/")
                                    if (fileName.isNotEmpty()) {
                                        val uploadFolder = File(context.filesDir, "upload")
                                        // 确保upload文件夹存在
                                        if (!uploadFolder.exists()) {
                                            uploadFolder.mkdirs()
                                            Log.i(
                                                TAG,
                                                "restoreFromBackupFile: Created upload directory"
                                            )
                                        }

                                        val targetFile = File(uploadFolder, fileName)
                                        Log.i(
                                            TAG,
                                            "restoreFromBackupFile: Restoring file ${zipEntry.name} to ${targetFile.absolutePath}"
                                        )

                                        try {
                                            FileOutputStream(targetFile).use { outputStream ->
                                                zipIn.copyTo(outputStream)
                                            }
                                            Log.i(
                                                TAG,
                                                "restoreFromBackupFile: Restored ${zipEntry.name} (${targetFile.length()} bytes)"
                                            )
                                        } catch (e: Exception) {
                                            Log.e(
                                                TAG,
                                                "restoreFromBackupFile: Failed to restore file ${zipEntry.name}",
                                                e
                                            )
                                            throw Exception("Failed to restore file ${zipEntry.name}: ${e.message}")
                                        }
                                    }
                                } else {
                                    Log.i(
                                        TAG,
                                        "restoreFromBackupFile: Skipping entry ${zipEntry.name}"
                                    )
                                }
                            }
                        }

                        zipIn.closeEntry()
                    }
                }
            }

            Log.i(TAG, "restoreFromBackupFile: Restore completed successfully")
        }
}

private fun addFileToZip(zipOut: ZipOutputStream, file: File, entryName: String) {
    FileInputStream(file).use { fis ->
        val zipEntry = ZipEntry(entryName)
        zipOut.putNextEntry(zipEntry)
        fis.copyTo(zipOut)
        zipOut.closeEntry()
        Log.d(TAG, "addFileToZip: Added $entryName (${file.length()} bytes) to zip")
    }
}

private fun addVirtualFileToZip(zipOut: ZipOutputStream, name: String, content: String) {
    val zipEntry = ZipEntry(name)
    zipOut.putNextEntry(zipEntry)
    zipOut.write(content.toByteArray())
    zipOut.closeEntry()
    Log.i(TAG, "addVirtualFileToZip: $name （${content.length} bytes）")
}

private fun WebDavConfig.requireClient(): HttpClient {
    val okHttpClient = HttpClient {
        install(HttpRedirect) {
            allowHttpsDowngrade = false
        }
        install(Auth) {
            digest {
                credentials {
                    DigestAuthCredentials(
                        username = this@requireClient.username,
                        password = this@requireClient.password
                    )
                }
            }
        }
        install(HttpSend)
        install(HttpTimeout) {
            socketTimeoutMillis = 5.minutes.inWholeMilliseconds
        }
    }.apply {
//        plugin(HttpSend).apply {
//            intercept { }
//        }
    }
    return okHttpClient
}

private fun WebDavConfig.requireCollection(path: String? = null): DavCollection {
    val location = Url(buildString {
        append(this@requireCollection.url.trimEnd('/'))
        append("/")
        if (this@requireCollection.path.isNotBlank()) {
            append(this@requireCollection.path.trim('/'))
            append("/")
        }
        if (path != null) {
            append(path.trim('/'))
        }
    })
    val davCollection = DavCollection(
        httpClient = this.requireClient(),
        location = location,
    )
    return davCollection
}

private suspend fun DavCollection.ensureCollectionExists() = withContext(Dispatchers.IO) {
    try {
        propfind(depth = 0, DisplayName.NAME) { response, relation ->
            Log.i(TAG, "ensureCollectionExists: $response $relation")
        }
    } catch (e: NotFoundException) {
        e.printStackTrace()
        Log.i(TAG, "ensureCollectionExists: ${this@ensureCollectionExists.location}")
        mkCol(null) { res ->
            Log.i(TAG, "ensureCollectionExists: $res")
        }
    }
}

data class BackupFileItem(
    val href: String,
    val displayName: String,
    val size: Long,
    val lastModified: Instant,
)
