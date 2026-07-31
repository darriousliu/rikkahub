package me.rerere.rikkahub.ui.pages.backup.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import kotlin.system.exitProcess
import me.rerere.rikkahub.generated.resources.*
import me.rerere.rikkahub.ui.resources.stringResource

@Composable
fun BackupDialog() {
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
