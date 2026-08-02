package me.rerere.rikkahub.data.sync

import io.github.vinceglb.filekit.delete
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.sink
import io.github.vinceglb.filekit.size
import io.github.vinceglb.filekit.source
import io.ktor.client.HttpClient
import io.ktor.utils.io.ByteReadChannel
import kotlinx.io.buffered
import me.rerere.common.crypto.PlatformSha256Crypto
import me.rerere.rikkahub.data.sync.s3.S3Client
import me.rerere.rikkahub.data.sync.s3.S3Config
import kotlin.time.Instant

class SharedS3BackupTransport(
    private val httpClient: HttpClient,
    private val archives: BackupArchiveService,
) : S3BackupTransport {
    override suspend fun testS3(config: S3Config): Int {
        client(config).listObjects(maxKeys = 1).getOrThrow()
        return 0
    }

    override suspend fun backupToS3(config: S3Config): Boolean {
        val archive = archives.prepareArchive(
            includeDatabase = S3Config.BackupItem.DATABASE in config.items,
            includeFiles = S3Config.BackupItem.FILES in config.items,
        )
        return try {
            client(config).putObject(
                key = "$BACKUP_PREFIX${archive.name}",
                contentLength = archive.size(),
                payloadHash = archives.sha256Hex(archive),
                content = { ByteReadChannel(archive.source().buffered()) },
                contentType = "application/zip",
            ).getOrThrow()
            true
        } finally {
            archive.delete(mustExist = false)
        }
    }

    override suspend fun listBackupFiles(config: S3Config): List<S3BackupItem> =
        client(config).listObjects(prefix = BACKUP_PREFIX, maxKeys = 1000).getOrThrow()
            .objects
            .filter { item -> item.key.startsWith("${BACKUP_PREFIX}backup_") && item.key.endsWith(".zip") }
            .map { item ->
                S3BackupItem(
                    key = item.key,
                    displayName = item.key.substringAfterLast('/'),
                    size = item.size,
                    lastModified = item.lastModified ?: Instant.fromEpochMilliseconds(0),
                )
            }
            .sortedByDescending(S3BackupItem::lastModified)

    override suspend fun restoreFromS3(config: S3Config, item: S3BackupItem): Int {
        val temporary = archives.createTemporaryArchive()
        try {
            temporary.sink().buffered().use { sink ->
                client(config).downloadObject(item.key) { buffer, byteCount ->
                    sink.write(buffer, startIndex = 0, endIndex = byteCount)
                }.getOrThrow()
            }
            archives.restoreArchive(
                archive = temporary,
                includeDatabase = S3Config.BackupItem.DATABASE in config.items,
                includeFiles = S3Config.BackupItem.FILES in config.items,
            )
            return 0
        } finally {
            temporary.delete(mustExist = false)
        }
    }

    override suspend fun deleteS3BackupFile(config: S3Config, item: S3BackupItem): Int {
        client(config).deleteObject(item.key).getOrThrow()
        return 0
    }

    private fun client(config: S3Config): S3Client =
        S3Client(config, httpClient, PlatformSha256Crypto)
}

private const val BACKUP_PREFIX = "rikkahub_backups/"
