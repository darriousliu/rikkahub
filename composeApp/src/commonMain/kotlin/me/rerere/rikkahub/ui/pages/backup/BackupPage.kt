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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import com.composables.icons.lucide.Cloud
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.DatabaseBackup
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
import me.rerere.rikkahub.ui.pages.backup.components.BackupDialog
import me.rerere.rikkahub.ui.pages.backup.tabs.ImportExportTab
import me.rerere.rikkahub.ui.pages.backup.tabs.S3Tab
import me.rerere.rikkahub.ui.pages.backup.tabs.WebDavTab
import org.koin.androidx.compose.koinViewModel
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
    val pagerState = rememberPagerState { 3 }
    val scope = rememberCoroutineScope()
    var showRestartDialog by remember { mutableStateOf(false) }

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
                        Icon(Lucide.Cloud, null)
                    },
                    label = {
                        Text(stringResource(Res.string.backup_page_s3_backup))
                    },
                    onClick = {
                        scope.launch { pagerState.animateScrollToPage(1) }
                    },
                )
                NavigationBarItem(
                    selected = pagerState.currentPage == 2,
                    icon = {
                        Icon(Lucide.Import, null)
                    },
                    label = {
                        Text(stringResource(Res.string.backup_page_import_export))
                    },
                    onClick = {
                        scope.launch { pagerState.animateScrollToPage(2) }
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
                    WebDavTab(
                        vm = vm,
                        onShowRestartDialog = { showRestartDialog = true }
                    )
                }

                1 -> {
                    S3Tab(
                        vm = vm,
                        onShowRestartDialog = { showRestartDialog = true }
                    )
                }

                2 -> {
                    ImportExportTab(
                        vm = vm,
                        onShowRestartDialog = { showRestartDialog = true }
                    )
                }
            }
        }
    }

    if (showRestartDialog) {
        BackupDialog()
    }
}
