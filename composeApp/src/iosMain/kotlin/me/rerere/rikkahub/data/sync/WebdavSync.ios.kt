package me.rerere.rikkahub.data.sync

import io.github.vinceglb.filekit.PlatformFile
import kotlinx.serialization.json.Json
import me.rerere.common.PlatformContext
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.WebDavConfig

// TODO("Not yet implemented")
actual class WebdavSync actual constructor(
    private val settingsStore: SettingsStore,
    private val json: Json,
    private val context: PlatformContext
) {
    actual suspend fun testWebdav(webDavConfig: WebDavConfig) {
    }

    actual suspend fun backupToWebDav(webDavConfig: WebDavConfig) {
    }

    actual suspend fun listBackupFiles(webDavConfig: WebDavConfig): List<WebDavBackupItem> {
        TODO("Not yet implemented")
    }

    actual suspend fun restoreFromWebDav(
        webDavConfig: WebDavConfig,
        item: WebDavBackupItem
    ) {
    }

    actual suspend fun deleteWebDavBackupFile(
        webDavConfig: WebDavConfig,
        item: WebDavBackupItem
    ) {
    }

    actual suspend fun restoreFromLocalFile(
        file: PlatformFile,
        webDavConfig: WebDavConfig
    ) {
    }

    actual suspend fun prepareBackupFile(webDavConfig: WebDavConfig): PlatformFile {
        TODO("Not yet implemented")
    }
}
