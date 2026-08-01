package me.rerere.rikkahub.data.sync

import me.rerere.rikkahub.data.datastore.WebDavConfig
import me.rerere.rikkahub.data.sync.s3.S3Config
import me.rerere.rikkahub.data.sync.webdav.WebDavBackupItem
import kotlin.time.Instant

interface WebDavBackupTransport {
    suspend fun testConnection(config: WebDavConfig): Int

    suspend fun backup(config: WebDavConfig): Boolean

    suspend fun listBackupFiles(config: WebDavConfig): List<WebDavBackupItem>

    suspend fun restore(config: WebDavConfig, item: WebDavBackupItem): Int

    suspend fun deleteBackupFile(config: WebDavConfig, item: WebDavBackupItem): Int
}

interface S3BackupTransport {
    suspend fun testS3(config: S3Config): Int

    suspend fun backupToS3(config: S3Config): Boolean

    suspend fun listBackupFiles(config: S3Config): List<S3BackupItem>

    suspend fun restoreFromS3(config: S3Config, item: S3BackupItem): Int

    suspend fun deleteS3BackupFile(config: S3Config, item: S3BackupItem): Int
}

data class S3BackupItem(
    val key: String,
    val displayName: String,
    val size: Long,
    val lastModified: Instant,
)
