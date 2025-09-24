package me.rerere.rikkahub.data.sync

import io.github.vinceglb.filekit.PlatformFile
import me.rerere.rikkahub.data.datastore.WebDavConfig

actual class WebdavSync {
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
