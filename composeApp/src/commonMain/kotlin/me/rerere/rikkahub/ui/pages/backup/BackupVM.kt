package me.rerere.rikkahub.ui.pages.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.repository.BackupLocalFileService
import me.rerere.rikkahub.data.repository.BackupRepository
import me.rerere.rikkahub.data.sync.S3BackupItem
import me.rerere.rikkahub.data.sync.webdav.WebDavBackupItem
import me.rerere.rikkahub.utils.UiState

typealias ChatboxRestoreResult = me.rerere.rikkahub.data.repository.ChatboxRestoreResult

class BackupVM(
    private val backupRepository: BackupRepository,
    private val localFileService: BackupLocalFileService,
) : ViewModel() {
    val settings = backupRepository.settings

    val webDavBackupItems = MutableStateFlow<UiState<List<WebDavBackupItem>>>(UiState.Idle)
    val s3BackupItems = MutableStateFlow<UiState<List<S3BackupItem>>>(UiState.Idle)

    init {
        loadBackupFileItems()
        loadS3BackupFileItems()
    }

    fun updateSettings(settings: Settings) {
        viewModelScope.launch { backupRepository.updateSettings(settings) }
    }

    fun loadBackupFileItems() {
        viewModelScope.launch {
            webDavBackupItems.value = UiState.Loading
            webDavBackupItems.value = runCatching { backupRepository.listWebDavBackups() }
                .fold(
                    onSuccess = { UiState.Success(it) },
                    onFailure = { UiState.Error(it) },
                )
        }
    }

    suspend fun testWebDav() = backupRepository.testWebDav()

    suspend fun backup() = backupRepository.backupWebDav()

    suspend fun restore(item: WebDavBackupItem) = backupRepository.restoreWebDav(item)

    suspend fun deleteWebDavBackupFile(item: WebDavBackupItem) = backupRepository.deleteWebDavBackup(item)

    suspend fun prepareExportFile(): PlatformFile = localFileService.prepareExport()

    suspend fun restoreFromLocalFile(source: PlatformFile) = localFileService.restoreBackup(source)

    suspend fun restoreFromChatboxFile(source: PlatformFile): ChatboxRestoreResult =
        localFileService.restoreChatbox(source)

    suspend fun restoreFromCherryStudioFile(source: PlatformFile) = localFileService.restoreCherryStudio(source)

    fun loadS3BackupFileItems() {
        viewModelScope.launch {
            s3BackupItems.value = UiState.Loading
            s3BackupItems.value = runCatching { backupRepository.listS3Backups() }
                .fold(
                    onSuccess = { UiState.Success(it) },
                    onFailure = { UiState.Error(it) },
                )
        }
    }

    suspend fun testS3() = backupRepository.testS3()

    suspend fun backupToS3() = backupRepository.backupToS3()

    suspend fun restoreFromS3(item: S3BackupItem) = backupRepository.restoreFromS3(item)

    suspend fun deleteS3BackupFile(item: S3BackupItem) = backupRepository.deleteS3Backup(item)
}
