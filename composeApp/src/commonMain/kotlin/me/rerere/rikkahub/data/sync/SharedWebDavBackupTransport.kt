package me.rerere.rikkahub.data.sync

import io.github.vinceglb.filekit.delete
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.sink
import io.github.vinceglb.filekit.size
import io.github.vinceglb.filekit.source
import io.ktor.client.HttpClient
import io.ktor.utils.io.ByteReadChannel
import kotlinx.io.buffered
import me.rerere.rikkahub.data.datastore.WebDavConfig
import me.rerere.rikkahub.data.sync.webdav.WebDavBackupItem
import me.rerere.rikkahub.data.sync.webdav.WebDavClient
import kotlin.time.Instant

class SharedWebDavBackupTransport(
    private val httpClient: HttpClient,
    private val archives: BackupArchiveService,
) : WebDavBackupTransport {
    override suspend fun testConnection(config: WebDavConfig): Int {
        WebDavClient(config, httpClient).propfind(depth = 0).getOrThrow()
        return 0
    }

    override suspend fun backup(config: WebDavConfig): Boolean {
        val archive = archives.prepareArchive(
            includeDatabase = WebDavConfig.BackupItem.DATABASE in config.items,
            includeFiles = WebDavConfig.BackupItem.FILES in config.items,
        )
        return try {
            val client = WebDavClient(config, httpClient)
            client.ensureCollectionExists().getOrThrow()
            client.put(
                path = archive.name,
                contentLength = archive.size(),
                content = { ByteReadChannel(archive.source().buffered()) },
                contentType = "application/zip",
            ).getOrThrow()
            true
        } finally {
            archive.delete(mustExist = false)
        }
    }

    override suspend fun listBackupFiles(config: WebDavConfig): List<WebDavBackupItem> {
        val client = WebDavClient(config, httpClient)
        client.ensureCollectionExists().getOrThrow()
        return client.list().getOrThrow()
            .filter { item ->
                !item.isCollection && item.displayName.startsWith("backup_") && item.displayName.endsWith(".zip")
            }
            .map { item ->
                WebDavBackupItem(
                    href = item.href,
                    displayName = item.displayName,
                    size = item.contentLength,
                    lastModified = item.lastModified ?: Instant.fromEpochMilliseconds(0),
                )
            }
            .sortedByDescending(WebDavBackupItem::lastModified)
    }

    override suspend fun restore(config: WebDavConfig, item: WebDavBackupItem): Int {
        val temporary = archives.createTemporaryArchive()
        try {
            temporary.sink().buffered().use { sink ->
                WebDavClient(config, httpClient).download(item.displayName) { buffer, byteCount ->
                    sink.write(buffer, startIndex = 0, endIndex = byteCount)
                }.getOrThrow()
            }
            archives.restoreArchive(
                archive = temporary,
                includeDatabase = WebDavConfig.BackupItem.DATABASE in config.items,
                includeFiles = WebDavConfig.BackupItem.FILES in config.items,
            )
            return 0
        } finally {
            temporary.delete(mustExist = false)
        }
    }

    override suspend fun deleteBackupFile(config: WebDavConfig, item: WebDavBackupItem): Int {
        WebDavClient(config, httpClient).delete(item.displayName).getOrThrow()
        return 0
    }
}
