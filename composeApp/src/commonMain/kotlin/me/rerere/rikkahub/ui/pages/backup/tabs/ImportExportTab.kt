package me.rerere.rikkahub.ui.pages.backup.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import coil3.compose.LocalPlatformContext
import com.composables.icons.lucide.File
import com.composables.icons.lucide.Import
import com.composables.icons.lucide.Lucide
import com.dokar.sonner.ToastType
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.cacheDir
import io.github.vinceglb.filekit.delete
import io.github.vinceglb.filekit.dialogs.compose.rememberFileSaverLauncher
import io.github.vinceglb.filekit.sink
import io.github.vinceglb.filekit.source
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.io.buffered
import me.rerere.rikkahub.ui.components.ui.StickyHeader
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.pages.backup.BackupVM
import me.rerere.rikkahub.utils.formatLocalizedTime
import me.rerere.rikkahub.utils.rememberFilePickerByMimeLauncher
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import rikkahub.composeapp.generated.resources.*
import kotlin.time.Clock

@Composable
fun ImportExportTab(
    vm: BackupVM,
    onShowRestartDialog: () -> Unit
) {
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val context = LocalPlatformContext.current
    var isExporting by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }

    // 导入类型：local 为本地备份，chatbox 为 Chatbox 导入
    var importType by remember { mutableStateOf("local") }

    // 创建文件保存的launcher
    val createDocumentLauncher = rememberFileSaverLauncher { file ->
        file?.let {
            scope.launch {
                isExporting = true
                runCatching {
                    // 导出文件
                    val exportFile = vm.exportToFile()

                    // 复制到用户选择的位置
                    file.sink().buffered().use { outputStream ->
                        exportFile.source().buffered().use { inputStream ->
                            outputStream.transferFrom(inputStream)
                        }
                    }

                    // 清理临时文件
                    exportFile.delete()

                    toaster.show(
                        getString(Res.string.backup_page_backup_success),
                        type = ToastType.Success
                    )
                }.onFailure { e ->
                    e.printStackTrace()
                    toaster.show(
                        getString(Res.string.backup_page_restore_failed, e.message ?: ""),
                        type = ToastType.Error
                    )
                }
                isExporting = false
            }
        }
    }

    // 创建文件选择的launcher
    val openDocumentLauncher = rememberFilePickerByMimeLauncher { file ->
        file?.let { sourceFile ->
            scope.launch {
                isRestoring = true
                runCatching {
                    when (importType) {
                        "local" -> {
                            // 本地备份导入：处理zip文件
                            val tempFile = PlatformFile(
                                FileKit.cacheDir,
                                "temp_restore_${Clock.System.now().toEpochMilliseconds()}.zip"
                            )

                            sourceFile.source().buffered().use { inputStream ->
                                tempFile.sink().buffered().use { outputStream ->
                                    outputStream.transferFrom(inputStream)
                                }
                            }

                            // 从临时文件恢复
                            vm.restoreFromLocalFile(tempFile)

                            // 清理临时文件
                            tempFile.delete()
                        }

                        "chatbox" -> {
                            // Chatbox导入：处理json文件
                            val tempFile = PlatformFile(
                                FileKit.cacheDir,
                                "temp_chatbox_${Clock.System.now().toEpochMilliseconds()}.json"
                            )

                            sourceFile.source().buffered().use { inputStream ->
                                tempFile.sink().buffered().use { outputStream ->
                                    outputStream.transferFrom(inputStream)
                                }
                            }

                            // 从Chatbox文件恢复
                            vm.restoreFromChatBox(tempFile)

                            // 清理临时文件
                            tempFile.delete()
                        }
                    }

                    toaster.show(
                        getString(Res.string.backup_page_restore_success),
                        type = ToastType.Success
                    )
                    onShowRestartDialog()
                }.onFailure { e ->
                    e.printStackTrace()
                    toaster.show(
                        getString(Res.string.backup_page_restore_failed, e.message ?: ""),
                        type = ToastType.Error
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
            Card(
                onClick = {
                    if (!isExporting) {
                        val timestamp = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                            .formatLocalizedTime("yyyyMMdd_HHmmss")
                        createDocumentLauncher.launch("rikkahub_backup_$timestamp.zip")
                    }
                },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                ListItem(
                    headlineContent = {
                        Text(stringResource(Res.string.backup_page_local_backup_export))
                    },
                    supportingContent = {
                        Text(
                            if (isExporting) stringResource(Res.string.backup_page_exporting) else stringResource(
                                Res.string.backup_page_export_desc
                            )
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = {
                        if (isExporting) {
                            CircularWavyProgressIndicator(
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Icon(Lucide.File, null)
                        }
                    }
                )
            }
        }

        item {
            Card(
                onClick = {
                    if (!isRestoring) {
                        importType = "local"
                        openDocumentLauncher.launch(arrayOf("application/zip"))
                    }
                },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                ListItem(
                    headlineContent = {
                        Text(stringResource(Res.string.backup_page_local_backup_import))
                    },
                    supportingContent = {
                        Text(
                            if (isRestoring) stringResource(Res.string.backup_page_importing) else stringResource(
                                Res.string.backup_page_import_desc
                            )
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = {
                        if (isRestoring) {
                            CircularWavyProgressIndicator(
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Icon(Lucide.Import, null)
                        }
                    }
                )
            }
        }

        stickyHeader {
            StickyHeader {
                Text(stringResource(Res.string.backup_page_import_from_other_app))
            }
        }

        item {
            Card(
                onClick = {
                    if (!isRestoring) {
                        importType = "chatbox"
                        openDocumentLauncher.launch(arrayOf("application/json"))
                    }
                },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                ListItem(
                    headlineContent = {
                        Text(stringResource(Res.string.backup_page_import_from_chatbox))
                    },
                    supportingContent = {
                        Text(stringResource(Res.string.backup_page_import_chatbox_desc))
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = {
                        if (isRestoring && importType == "chatbox") {
                            CircularWavyProgressIndicator(
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Icon(Lucide.Import, null)
                        }
                    }
                )
            }
        }
    }
}
