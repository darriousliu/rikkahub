package me.rerere.rikkahub.ui.pages.assistant.detail

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import me.rerere.rikkahub.data.ai.tools.local.LocalToolOption
import me.rerere.rikkahub.generated.resources.Res
import me.rerere.rikkahub.generated.resources.permission_calendar_read
import me.rerere.rikkahub.generated.resources.permission_calendar_read_desc
import me.rerere.rikkahub.generated.resources.permission_calendar_write
import me.rerere.rikkahub.generated.resources.permission_calendar_write_desc
import me.rerere.rikkahub.ui.components.ui.permission.PermissionInfo
import me.rerere.rikkahub.ui.components.ui.permission.PermissionManager
import me.rerere.rikkahub.ui.components.ui.permission.rememberPermissionState
import org.jetbrains.compose.resources.stringResource

internal actual val platformLocalToolOptions: Set<LocalToolOption> = setOf(
    LocalToolOption.JavascriptEngine,
    LocalToolOption.TimeInfo,
    LocalToolOption.Clipboard,
    LocalToolOption.Tts,
    LocalToolOption.AskUser,
    LocalToolOption.ScreenTime,
    LocalToolOption.Calendar,
)

@Composable
internal actual fun rememberLocalToolPermissionGate(
    onScreenTimePermissionRequired: () -> Unit,
): (option: LocalToolOption) -> Boolean {
    val context = LocalContext.current
    val currentPermissionWarning = rememberUpdatedState(onScreenTimePermissionRequired)
    val calendarPermissionState = rememberPermissionState(
        permissions = setOf(
            PermissionInfo(
                permission = Manifest.permission.READ_CALENDAR,
                displayName = { Text(stringResource(Res.string.permission_calendar_read)) },
                usage = { Text(stringResource(Res.string.permission_calendar_read_desc)) },
                required = true,
            ),
            PermissionInfo(
                permission = Manifest.permission.WRITE_CALENDAR,
                displayName = { Text(stringResource(Res.string.permission_calendar_write)) },
                usage = { Text(stringResource(Res.string.permission_calendar_write_desc)) },
                required = true,
            ),
        ),
    )
    PermissionManager(permissionState = calendarPermissionState)

    return remember(context, calendarPermissionState) {
        { option ->
            when {
                option == LocalToolOption.ScreenTime && !context.hasUsageStatsPermission() -> {
                    currentPermissionWarning.value()
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                    }
                    true
                }

                option == LocalToolOption.Calendar && !calendarPermissionState.allPermissionsGranted -> {
                    calendarPermissionState.requestPermissions()
                    false
                }

                else -> true
            }
        }
    }
}

private fun Context.hasUsageStatsPermission(): Boolean {
    val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            packageName,
        )
    } else {
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            packageName,
        )
    }
    return mode == AppOpsManager.MODE_ALLOWED
}
