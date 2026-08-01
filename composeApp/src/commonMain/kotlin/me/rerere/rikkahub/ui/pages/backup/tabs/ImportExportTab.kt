package me.rerere.rikkahub.ui.pages.backup.tabs

import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.File01
import me.rerere.hugeicons.stroke.FileImport
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dokar.sonner.ToastType
import kotlinx.coroutines.launch
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.dialogs.compose.rememberFileSaverLauncher
import io.github.vinceglb.filekit.delete
import io.github.vinceglb.filekit.write
import me.rerere.common.time.toCompactFileTimestamp
import me.rerere.rikkahub.generated.resources.*
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.StickyHeader
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.pages.backup.BackupVM
import kotlin.time.Clock
import org.jetbrains.compose.resources.stringResource

private enum class BackupImportType {
    LOCAL,
    CHATBOX,
    CHERRY,
}

@Composable
fun ImportExportTab(
    vm: BackupVM,
    onShowRestartDialog: () -> Unit
) {
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var isExporting by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }
    var importType by remember { mutableStateOf(BackupImportType.LOCAL) }
    val backupSuccess = stringResource(Res.string.backup_page_backup_success)
    val restoreSuccess = stringResource(Res.string.backup_page_restore_success)
    val restoreFailedPrefix = stringResource(Res.string.backup_page_restore_failed, "")

    val createDocumentLauncher = rememberFileSaverLauncher(
        dialogSettings = FileKitDialogSettings.createDefault(),
    ) { target ->
        if (target != null) {
            scope.launch {
                isExporting = true
                runCatching {
                    val exportFile = vm.prepareExportFile()
                    try {
                        target.write(exportFile)
                    } finally {
                        exportFile.delete(mustExist = false)
                    }
                    toaster.show(backupSuccess, type = ToastType.Success)
                }.onFailure { e ->
                    toaster.show(
                        restoreFailedPrefix + (e.message ?: ""),
                        type = ToastType.Error,
                    )
                }
                isExporting = false
            }
        }
    }

    val openDocumentLauncher = rememberFilePickerLauncher(type = FileKitType.File()) { source ->
        if (source != null) {
            scope.launch {
                isRestoring = true
                runCatching {
                    when (importType) {
                        BackupImportType.LOCAL -> vm.restoreFromLocalFile(source)
                        BackupImportType.CHATBOX -> vm.restoreFromChatboxFile(source)
                        BackupImportType.CHERRY -> vm.restoreFromCherryStudioFile(source)
                    }
                    toaster.show(restoreSuccess, type = ToastType.Success)
                    onShowRestartDialog()
                }.onFailure { e ->
                    toaster.show(
                        restoreFailedPrefix + (e.message ?: ""),
                        type = ToastType.Error,
                    )
                }
                isRestoring = false
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(16.dp)
    ) {
        stickyHeader {
            StickyHeader {
                Text(stringResource(Res.string.backup_page_local_backup_export))
            }
        }

        item {
            CardGroup {
                item(
                    onClick = if (!isExporting) {
                        {
                            val timestamp = Clock.System.now().toCompactFileTimestamp()
                            createDocumentLauncher.launch(
                                suggestedName = "rikkahub_backup_$timestamp",
                                defaultExtension = "zip",
                                allowedExtensions = setOf("zip"),
                            )
                        }
                    } else null,
                    headlineContent = { Text(stringResource(Res.string.backup_page_local_backup_export)) },
                    supportingContent = {
                        Text(
                            if (isExporting) {
                                stringResource(Res.string.backup_page_exporting)
                            } else {
                                stringResource(Res.string.backup_page_export_desc)
                            }
                        )
                    },
                    leadingContent = {
                        if (isExporting) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            Icon(HugeIcons.File01, null)
                        }
                    },
                )

                item(
                    onClick = if (!isRestoring) {
                        {
                            importType = BackupImportType.LOCAL
                            openDocumentLauncher.launch()
                        }
                    } else null,
                    headlineContent = { Text(stringResource(Res.string.backup_page_local_backup_import)) },
                    supportingContent = {
                        Text(
                            if (isRestoring) {
                                stringResource(Res.string.backup_page_importing)
                            } else {
                                stringResource(Res.string.backup_page_import_desc)
                            }
                        )
                    },
                    leadingContent = {
                        if (isRestoring) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            Icon(HugeIcons.FileImport, null)
                        }
                    },
                )
            }
        }

        stickyHeader {
            StickyHeader {
                Text(stringResource(Res.string.backup_page_import_from_other_app))
            }
        }

        item {
            CardGroup {
                item(
                    onClick = if (!isRestoring) {
                        {
                            importType = BackupImportType.CHATBOX
                            openDocumentLauncher.launch()
                        }
                    } else null,
                    headlineContent = { Text(stringResource(Res.string.backup_page_import_from_chatbox)) },
                    supportingContent = { Text(stringResource(Res.string.backup_page_import_chatbox_desc)) },
                    leadingContent = {
                        if (isRestoring && importType == BackupImportType.CHATBOX) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            Icon(HugeIcons.FileImport, null)
                        }
                    },
                )

                item(
                    onClick = if (!isRestoring) {
                        {
                            importType = BackupImportType.CHERRY
                            openDocumentLauncher.launch()
                        }
                    } else null,
                    headlineContent = { Text(stringResource(Res.string.backup_page_import_from_cherry_studio)) },
                    supportingContent = { Text(stringResource(Res.string.backup_page_import_cherry_studio_desc)) },
                    leadingContent = {
                        if (isRestoring && importType == BackupImportType.CHERRY) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            Icon(HugeIcons.FileImport, null)
                        }
                    },
                )
            }
        }
    }
}
