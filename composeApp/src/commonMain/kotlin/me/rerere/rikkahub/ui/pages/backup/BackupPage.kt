package me.rerere.rikkahub.ui.pages.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MultiChoiceSegmentedButtonRow
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.DatabaseBackup
import com.composables.icons.lucide.Eye
import com.composables.icons.lucide.EyeOff
import com.composables.icons.lucide.File
import com.composables.icons.lucide.Import
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Upload
import com.dokar.sonner.ToastType
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.cacheDir
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.dialogs.compose.rememberFileSaverLauncher
import io.github.vinceglb.filekit.write
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import me.rerere.common.utils.delete
import me.rerere.common.utils.exitProcess
import me.rerere.rikkahub.data.datastore.WebDavConfig
import me.rerere.rikkahub.data.sync.WebDavBackupItem
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.FormItem
import me.rerere.rikkahub.ui.components.ui.StickyHeader
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.utils.fileSizeToString
import me.rerere.rikkahub.utils.onError
import me.rerere.rikkahub.utils.onLoading
import me.rerere.rikkahub.utils.onSuccess
import me.rerere.rikkahub.utils.toLocalDateTime
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import rikkahub.composeapp.generated.resources.Res
import rikkahub.composeapp.generated.resources.backup_page_backing_up
import rikkahub.composeapp.generated.resources.backup_page_backup_items
import rikkahub.composeapp.generated.resources.backup_page_backup_now
import rikkahub.composeapp.generated.resources.backup_page_backup_success
import rikkahub.composeapp.generated.resources.backup_page_chat_records
import rikkahub.composeapp.generated.resources.backup_page_connection_failed
import rikkahub.composeapp.generated.resources.backup_page_connection_success
import rikkahub.composeapp.generated.resources.backup_page_delete
import rikkahub.composeapp.generated.resources.backup_page_delete_failed
import rikkahub.composeapp.generated.resources.backup_page_delete_success
import rikkahub.composeapp.generated.resources.backup_page_export_desc
import rikkahub.composeapp.generated.resources.backup_page_exporting
import rikkahub.composeapp.generated.resources.backup_page_files
import rikkahub.composeapp.generated.resources.backup_page_import_chatbox_desc
import rikkahub.composeapp.generated.resources.backup_page_import_desc
import rikkahub.composeapp.generated.resources.backup_page_import_export
import rikkahub.composeapp.generated.resources.backup_page_import_from_chatbox
import rikkahub.composeapp.generated.resources.backup_page_import_from_other_app
import rikkahub.composeapp.generated.resources.backup_page_importing
import rikkahub.composeapp.generated.resources.backup_page_loading_failed
import rikkahub.composeapp.generated.resources.backup_page_local_backup_export
import rikkahub.composeapp.generated.resources.backup_page_local_backup_import
import rikkahub.composeapp.generated.resources.backup_page_password
import rikkahub.composeapp.generated.resources.backup_page_path
import rikkahub.composeapp.generated.resources.backup_page_restart_app
import rikkahub.composeapp.generated.resources.backup_page_restart_desc
import rikkahub.composeapp.generated.resources.backup_page_restore
import rikkahub.composeapp.generated.resources.backup_page_restore_failed
import rikkahub.composeapp.generated.resources.backup_page_restore_now
import rikkahub.composeapp.generated.resources.backup_page_restore_success
import rikkahub.composeapp.generated.resources.backup_page_restoring
import rikkahub.composeapp.generated.resources.backup_page_test_connection
import rikkahub.composeapp.generated.resources.backup_page_title
import rikkahub.composeapp.generated.resources.backup_page_unknown_error
import rikkahub.composeapp.generated.resources.backup_page_username
import rikkahub.composeapp.generated.resources.backup_page_webdav_backup
import rikkahub.composeapp.generated.resources.backup_page_webdav_backup_files
import rikkahub.composeapp.generated.resources.backup_page_webdav_server_address
import kotlin.time.Clock


@Composable
fun BackupPage(vm: BackupVM = koinViewModel()) {
    val pagerState = rememberPagerState { 2 }
    val scope = rememberCoroutineScope()
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(Res.string.backup_page_title))
                },
                navigationIcon = {
                    BackButton()
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = pagerState.currentPage == 0,
                    icon = {
                        Icon(Lucide.DatabaseBackup, null)
                    },
                    label = {
                        Text(stringResource(Res.string.backup_page_webdav_backup))
                    },
                    onClick = {
                        scope.launch { pagerState.animateScrollToPage(0) }
                    },
                )
                NavigationBarItem(
                    selected = pagerState.currentPage == 1,
                    icon = {
                        Icon(Lucide.Import, null)
                    },
                    label = {
                        Text(stringResource(Res.string.backup_page_import_export))
                    },
                    onClick = {
                        scope.launch { pagerState.animateScrollToPage(1) }
                    },
                )
            }
        }
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = it
        ) { page ->
            when (page) {
                0 -> {
                    WebDavPage(vm)
                }

                1 -> {
                    ImportExportPage(vm)
                }
            }
        }
    }
}

@Composable
private fun WebDavPage(
    vm: BackupVM
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val webDavConfig = settings.webDavConfig
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var showBackupFiles by remember { mutableStateOf(false) }
    var showRestartDialog by remember { mutableStateOf(false) }
    var restoringItemId by remember { mutableStateOf<String?>(null) }
    var isBackingUp by remember { mutableStateOf(false) }

    fun updateWebDavConfig(newConfig: WebDavConfig) {
        vm.updateSettings(settings.copy(webDavConfig = newConfig))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FormItem(
                    label = { Text(stringResource(Res.string.backup_page_webdav_server_address)) }
                ) {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = webDavConfig.url,
                        onValueChange = { updateWebDavConfig(webDavConfig.copy(url = it.trim())) },
                        placeholder = { Text("https://example.com/dav") },
                        singleLine = true
                    )
                }
                FormItem(
                    label = { Text(stringResource(Res.string.backup_page_username)) }
                ) {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = webDavConfig.username,
                        onValueChange = {
                            updateWebDavConfig(
                                webDavConfig.copy(
                                    username = it.trim()
                                )
                            )
                        },
                        singleLine = true
                    )
                }
                FormItem(
                    label = { Text(stringResource(Res.string.backup_page_password)) }
                ) {
                    var passwordVisible by remember { mutableStateOf(false) }
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = webDavConfig.password,
                        onValueChange = { updateWebDavConfig(webDavConfig.copy(password = it)) },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            val image = if (passwordVisible)
                                Lucide.EyeOff
                            else
                                Lucide.Eye
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(imageVector = image, null)
                            }
                        },
                        singleLine = true
                    )
                }
                FormItem(
                    label = { Text(stringResource(Res.string.backup_page_path)) }
                ) {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = webDavConfig.path,
                        onValueChange = { updateWebDavConfig(webDavConfig.copy(path = it.trim())) },
                        singleLine = true
                    )
                }
            }
        }

        Card {
            FormItem(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                label = {
                    Text(stringResource(Res.string.backup_page_backup_items))
                }
            ) {
                MultiChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    WebDavConfig.BackupItem.entries.forEachIndexed { index, item ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = WebDavConfig.BackupItem.entries.size
                            ),
                            onCheckedChange = {
                                val newItems = if (it) {
                                    webDavConfig.items + item
                                } else {
                                    webDavConfig.items - item
                                }
                                updateWebDavConfig(webDavConfig.copy(items = newItems))
                            },
                            checked = item in webDavConfig.items
                        ) {
                            Text(
                                when (item) {
                                    WebDavConfig.BackupItem.DATABASE -> stringResource(Res.string.backup_page_chat_records)
                                    WebDavConfig.BackupItem.FILES -> stringResource(Res.string.backup_page_files)
                                }
                            )
                        }
                    }
                }
            }
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
        ) {
            OutlinedButton(
                onClick = {
                    scope.launch {
                        try {
                            vm.testWebDav()
                            toaster.show(
                                getString(Res.string.backup_page_connection_success),
                                type = ToastType.Success
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                            toaster.show(
                                getString(
                                    Res.string.backup_page_connection_failed,
                                    e.message ?: ""
                                ), type = ToastType.Error
                            )
                        }
                    }
                }
            ) {
                Text(stringResource(Res.string.backup_page_test_connection))
            }
            OutlinedButton(
                onClick = {
                    showBackupFiles = true
                }
            ) {
                Text(stringResource(Res.string.backup_page_restore))
            }

            Button(
                onClick = {
                    scope.launch {
                        isBackingUp = true
                        runCatching {
                            vm.backup()
                            vm.loadBackupFileItems()
                            toaster.show(
                                getString(Res.string.backup_page_backup_success),
                                type = ToastType.Success
                            )
                        }.onFailure {
                            it.printStackTrace()
                            toaster.show(
                                it.message ?: getString(Res.string.backup_page_unknown_error),
                                type = ToastType.Error
                            )
                        }
                        isBackingUp = false
                    }
                },
                enabled = !isBackingUp
            ) {
                if (isBackingUp) {
                    CircularWavyProgressIndicator(
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Icon(Lucide.Upload, null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(if (isBackingUp) stringResource(Res.string.backup_page_backing_up) else stringResource(Res.string.backup_page_backup_now))
            }
        }
    }

    if (showBackupFiles) {
        ModalBottomSheet(
            onDismissRequest = {
                showBackupFiles = false
            },
            sheetState = rememberModalBottomSheetState(
                skipPartiallyExpanded = true
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.8f)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    stringResource(Res.string.backup_page_webdav_backup_files),
                    modifier = Modifier.fillMaxWidth()
                )
                val backupItems by vm.webDavBackupItems.collectAsStateWithLifecycle()
                backupItems.onSuccess {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(it) { item ->
                            BackupItemCard(
                                item = item,
                                isRestoring = restoringItemId == item.displayName,
                                onDelete = {
                                    scope.launch {
                                        runCatching {
                                            vm.deleteWebDavBackupFile(item)
                                            toaster.show(
                                                getString(Res.string.backup_page_delete_success),
                                                type = ToastType.Success
                                            )
                                            vm.loadBackupFileItems()
                                        }.onFailure { err ->
                                            err.printStackTrace()
                                            toaster.show(
                                                getString(
                                                    Res.string.backup_page_delete_failed,
                                                    err.message ?: ""
                                                ),
                                                type = ToastType.Error
                                            )
                                        }
                                    }
                                },
                                onRestore = { item ->
                                    scope.launch {
                                        restoringItemId = item.displayName
                                        runCatching {
                                            vm.restore(item = item)
                                            toaster.show(
                                                getString(Res.string.backup_page_restore_success),
                                                type = ToastType.Success
                                            )
                                            showBackupFiles = false
                                            showRestartDialog = true
                                        }.onFailure { err ->
                                            err.printStackTrace()
                                            toaster.show(
                                                getString(
                                                    Res.string.backup_page_restore_failed,
                                                    err.message ?: ""
                                                ),
                                                type = ToastType.Error
                                            )
                                        }
                                        restoringItemId = null
                                    }
                                },
                            )
                        }
                    }
                }.onError {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(Res.string.backup_page_loading_failed, it.message ?: ""),
                            color = Color.Red
                        )
                    }
                }.onLoading {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularWavyProgressIndicator()
                    }
                }
            }
        }
    }

    if (showRestartDialog) {
        BackupDialog()
    }
}

@Composable
private fun BackupItemCard(
    item: WebDavBackupItem,
    isRestoring: Boolean = false,
    onDelete: (WebDavBackupItem) -> Unit = {},
    onRestore: (WebDavBackupItem) -> Unit = {},
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = item.displayName,
                style = MaterialTheme.typography.titleMedium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.lastModified.toLocalDateTime(),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = item.size.fileSizeToString(),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = {
                    onDelete(item)
                },
                enabled = !isRestoring
            ) {
                Text(stringResource(Res.string.backup_page_delete))
            }
            Button(
                onClick = {
                    onRestore(item)
                },
                enabled = !isRestoring
            ) {
                if (isRestoring) {
                    CircularWavyProgressIndicator(
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (isRestoring) stringResource(Res.string.backup_page_restoring) else stringResource(Res.string.backup_page_restore_now))
            }
        }
    }
}

@Composable
private fun ImportExportPage(
    vm: BackupVM
) {
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var isExporting by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }
    var showRestartDialog by remember { mutableStateOf(false) }

    // 导入类型：local 为本地备份，chatbox 为 Chatbox 导入
    var importType by remember { mutableStateOf("local") }

    // 创建文件保存的launcher
    val createDocumentLauncher = rememberFileSaverLauncher { uri ->
        uri?.let { targetUri ->
            scope.launch {
                isExporting = true
                runCatching {
                    // 导出文件
                    val exportFile = vm.exportToFile()

                    // 复制到用户选择的位置
                    targetUri.write(exportFile)

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
    val openDocumentLauncher = rememberFilePickerLauncher(
        type = FileKitType.File(if (importType == "local") "zip" else "json"),
    ) { uri ->
        uri?.let { sourceUri ->
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

                            tempFile.write(sourceUri)

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

                            tempFile.write(sourceUri)

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
                    showRestartDialog = true
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
                            .format(
                                LocalDateTime
                                    .Format {
                                        year()
                                        monthNumber()
                                        day()
                                        char('_')
                                        hour()
                                        minute()
                                        second()
                                    },
                            )
                        createDocumentLauncher.launch("rikkahub_backup_$timestamp", "zip")
                    }
                }
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
                        openDocumentLauncher.launch()
                    }
                }
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
                        openDocumentLauncher.launch()
                    }
                }
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

    // 重启对话框
    if (showRestartDialog) {
        BackupDialog()
    }
}

@Composable
private fun BackupDialog() {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(Res.string.backup_page_restart_app)) },
        text = { Text(stringResource(Res.string.backup_page_restart_desc)) },
        confirmButton = {
            Button(
                onClick = {
                    exitProcess(0)
                }
            ) {
                Text(stringResource(Res.string.backup_page_restart_app))
            }
        },
    )
}
