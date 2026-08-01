package me.rerere.rikkahub.data.repository

import kotlinx.coroutines.flow.StateFlow
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.sync.S3BackupItem
import me.rerere.rikkahub.data.sync.S3BackupTransport
import me.rerere.rikkahub.data.sync.WebDavBackupTransport
import me.rerere.rikkahub.data.sync.webdav.WebDavBackupItem
import kotlin.time.Clock

interface BackupSettingsGateway {
    val settings: StateFlow<Settings>

    suspend fun update(settings: Settings)

    suspend fun update(transform: (Settings) -> Settings)
}

class SettingsStoreBackupSettingsGateway(
    private val store: SettingsStore,
) : BackupSettingsGateway {
    override val settings: StateFlow<Settings> = store.settingsFlow

    override suspend fun update(settings: Settings) = store.update(settings)

    override suspend fun update(transform: (Settings) -> Settings) = store.update(transform)
}

class BackupRepository(
    private val settingsGateway: BackupSettingsGateway,
    private val webDavTransport: WebDavBackupTransport,
    private val s3Transport: S3BackupTransport,
    private val clock: Clock = Clock.System,
) {
    val settings: StateFlow<Settings> = settingsGateway.settings

    suspend fun updateSettings(settings: Settings) = settingsGateway.update(settings)

    suspend fun listWebDavBackups(): List<WebDavBackupItem> =
        webDavTransport.listBackupFiles(settings.value.webDavConfig)
            .sortedByDescending(WebDavBackupItem::lastModified)

    suspend fun testWebDav() = webDavTransport.testConnection(settings.value.webDavConfig)

    suspend fun backupWebDav() {
        webDavTransport.backup(settings.value.webDavConfig)
        recordBackupCompleted()
    }

    suspend fun restoreWebDav(item: WebDavBackupItem) =
        webDavTransport.restore(settings.value.webDavConfig, item)

    suspend fun deleteWebDavBackup(item: WebDavBackupItem) =
        webDavTransport.deleteBackupFile(settings.value.webDavConfig, item)

    suspend fun listS3Backups(): List<S3BackupItem> =
        s3Transport.listBackupFiles(settings.value.s3Config)

    suspend fun testS3() = s3Transport.testS3(settings.value.s3Config)

    suspend fun backupToS3() {
        s3Transport.backupToS3(settings.value.s3Config)
        recordBackupCompleted()
    }

    suspend fun restoreFromS3(item: S3BackupItem) =
        s3Transport.restoreFromS3(settings.value.s3Config, item)

    suspend fun deleteS3Backup(item: S3BackupItem) =
        s3Transport.deleteS3BackupFile(settings.value.s3Config, item)

    suspend fun recordBackupCompleted() {
        settingsGateway.update { settings ->
            settings.copy(
                backupReminderConfig = settings.backupReminderConfig.copy(
                    lastBackupTime = clock.now().toEpochMilliseconds(),
                )
            )
        }
    }
}
