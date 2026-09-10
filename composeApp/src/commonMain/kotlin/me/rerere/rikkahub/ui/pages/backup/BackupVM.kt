package me.rerere.rikkahub.ui.pages.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.repository.BackupLocalFileService
import me.rerere.rikkahub.data.sync.S3BackupItem
import me.rerere.rikkahub.data.sync.S3BackupTransport
import me.rerere.rikkahub.data.sync.WebDavBackupTransport
import me.rerere.rikkahub.data.sync.webdav.WebDavBackupItem
import me.rerere.rikkahub.utils.UiState
import kotlin.time.Clock

typealias ChatboxRestoreResult = me.rerere.rikkahub.data.repository.ChatboxRestoreResult

class BackupVM(
    private val settingsStore: SettingsStore,
    private val webDavSync: WebDavBackupTransport,
    private val s3Sync: S3BackupTransport,
    private val localFileService: BackupLocalFileService,
    private val clock: Clock = Clock.System,
) : ViewModel() {
    val settings = settingsStore.settingsFlow

    val webDavBackupItems = MutableStateFlow<UiState<List<WebDavBackupItem>>>(UiState.Idle)
    val s3BackupItems = MutableStateFlow<UiState<List<S3BackupItem>>>(UiState.Idle)

    init {
        loadBackupFileItems()
        loadS3BackupFileItems()
    }

    fun updateSettings(settings: Settings) {
        viewModelScope.launch { settingsStore.update(settings) }
    }

    fun loadBackupFileItems() {
        viewModelScope.launch {
            webDavBackupItems.value = UiState.Loading
            webDavBackupItems.value = runCatching {
                webDavSync.listBackupFiles(settings.value.webDavConfig)
                    .sortedByDescending(WebDavBackupItem::lastModified)
            }
                .fold(
                    onSuccess = { UiState.Success(it) },
                    onFailure = { UiState.Error(it) },
                )
        }
    }

    suspend fun testWebDav() = webDavSync.testConnection(settings.value.webDavConfig)

    suspend fun backup() {
        webDavSync.backup(settings.value.webDavConfig)
        recordBackupTime()
    }

    suspend fun restore(item: WebDavBackupItem) = webDavSync.restore(settings.value.webDavConfig, item)

    suspend fun deleteWebDavBackupFile(item: WebDavBackupItem) =
        webDavSync.deleteBackupFile(settings.value.webDavConfig, item)

    suspend fun prepareExportFile(): PlatformFile = localFileService.prepareExport()

    suspend fun restoreFromLocalFile(source: PlatformFile) = localFileService.restoreBackup(source)

    suspend fun restoreFromChatboxFile(source: PlatformFile): ChatboxRestoreResult =
        localFileService.restoreChatbox(source)

    suspend fun restoreFromCherryStudioFile(source: PlatformFile) = localFileService.restoreCherryStudio(source)

    fun loadS3BackupFileItems() {
        viewModelScope.launch {
            s3BackupItems.value = UiState.Loading
            s3BackupItems.value = runCatching { s3Sync.listBackupFiles(settings.value.s3Config) }
                .fold(
                    onSuccess = { UiState.Success(it) },
                    onFailure = { UiState.Error(it) },
                )
        }
    }

    suspend fun testS3() = s3Sync.testS3(settings.value.s3Config)

    suspend fun backupToS3() {
        s3Sync.backupToS3(settings.value.s3Config)
        recordBackupTime()
    }

    suspend fun restoreFromS3(item: S3BackupItem) = s3Sync.restoreFromS3(settings.value.s3Config, item)

    suspend fun deleteS3BackupFile(item: S3BackupItem) = s3Sync.deleteS3BackupFile(settings.value.s3Config, item)

    private suspend fun recordBackupTime() {
        settingsStore.update { settings ->
            settings.copy(
                backupReminderConfig = settings.backupReminderConfig.copy(
                    lastBackupTime = clock.now().toEpochMilliseconds(),
                )
            )
        }
    }
}
