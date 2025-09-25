package me.rerere.rikkahub.data.sync

import io.github.vinceglb.filekit.PlatformFile
import kotlinx.serialization.json.Json
import me.rerere.common.PlatformContext
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.WebDavConfig
import kotlin.time.Instant

expect class WebdavSync(settingsStore: SettingsStore, json: Json, context: PlatformContext) {
    suspend fun testWebdav(webDavConfig: WebDavConfig)
    suspend fun backupToWebDav(webDavConfig: WebDavConfig)
    suspend fun listBackupFiles(webDavConfig: WebDavConfig): List<WebDavBackupItem>
    suspend fun restoreFromWebDav(webDavConfig: WebDavConfig, item: WebDavBackupItem)
    suspend fun deleteWebDavBackupFile(webDavConfig: WebDavConfig, item: WebDavBackupItem)
    suspend fun restoreFromLocalFile(file: PlatformFile, webDavConfig: WebDavConfig)
    suspend fun prepareBackupFile(webDavConfig: WebDavConfig): PlatformFile
}

data class WebDavBackupItem(
    val href: String,
    val displayName: String,
    val size: Long,
    val lastModified: Instant,
)

