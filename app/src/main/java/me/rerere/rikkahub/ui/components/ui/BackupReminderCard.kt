package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.DatabaseRestore
import me.rerere.rikkahub.data.datastore.BackupReminderConfig
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.generated.resources.*
import me.rerere.rikkahub.ui.resources.stringResource

@Composable
fun BackupReminderCard(
    settings: Settings,
    onClick: () -> Unit,
) {
    val config = settings.backupReminderConfig
    var dismissed by remember { mutableStateOf(false) }
    val nowMillis = System.currentTimeMillis()

    val reminder = calculateBackupReminder(config, nowMillis)

    if (!reminder.isDue || dismissed) return

    Card(onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = HugeIcons.DatabaseRestore,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.backup_page_reminder_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                val lastBackupText = if (config.lastBackupTime == 0L) {
                    stringResource(Res.string.backup_page_reminder_never_backed_up)
                } else {
                    stringResource(Res.string.backup_page_reminder_last_days, reminder.daysSinceLastBackup)
                }
                Text(
                    text = lastBackupText,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(
                onClick = { dismissed = true },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = HugeIcons.Cancel01,
                    contentDescription = stringResource(Res.string.backup_page_reminder_dismiss),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

internal data class BackupReminderState(
    val isDue: Boolean,
    val daysSinceLastBackup: Long,
)

internal fun calculateBackupReminder(
    config: BackupReminderConfig,
    nowMillis: Long,
): BackupReminderState {
    val elapsedMillis = nowMillis - config.lastBackupTime
    return BackupReminderState(
        isDue = config.enabled && elapsedMillis > config.intervalDays * MILLIS_PER_DAY,
        daysSinceLastBackup = elapsedMillis / MILLIS_PER_DAY,
    )
}

private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
