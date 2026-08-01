package me.rerere.rikkahub.ui.components.ui.permission

import android.Manifest
import android.os.Build
import androidx.compose.material3.Text
import me.rerere.rikkahub.generated.resources.Res
import me.rerere.rikkahub.generated.resources.permission_camera
import me.rerere.rikkahub.generated.resources.permission_camera_desc
import me.rerere.rikkahub.generated.resources.permission_local_network
import me.rerere.rikkahub.generated.resources.permission_local_network_desc
import me.rerere.rikkahub.generated.resources.permission_microphone
import me.rerere.rikkahub.generated.resources.permission_microphone_desc
import me.rerere.rikkahub.generated.resources.permission_notification
import me.rerere.rikkahub.generated.resources.permission_notification_desc
import org.jetbrains.compose.resources.stringResource

actual val PermissionCamera: PermissionInfo = PermissionInfo(
    permission = Manifest.permission.CAMERA,
    displayName = { Text(stringResource(Res.string.permission_camera)) },
    usage = { Text(stringResource(Res.string.permission_camera_desc)) },
    required = true,
)

actual val PermissionRecordAudio: PermissionInfo = PermissionInfo(
    permission = Manifest.permission.RECORD_AUDIO,
    displayName = { Text(stringResource(Res.string.permission_microphone)) },
    usage = { Text(stringResource(Res.string.permission_microphone_desc)) },
    required = true,
)

actual val PermissionNotification: PermissionInfo = PermissionInfo(
    permission = Manifest.permission.POST_NOTIFICATIONS,
    displayName = { Text(stringResource(Res.string.permission_notification)) },
    usage = { Text(stringResource(Res.string.permission_notification_desc)) },
    required = true,
)

actual val RuntimeNotificationPermissionRequired: Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

actual val PermissionLocalNetwork: PermissionInfo = PermissionInfo(
    permission = Manifest.permission.ACCESS_LOCAL_NETWORK,
    displayName = { Text(stringResource(Res.string.permission_local_network)) },
    usage = { Text(stringResource(Res.string.permission_local_network_desc)) },
    required = true,
)

actual val RuntimeLocalNetworkPermissionRequired: Boolean = Build.VERSION.SDK_INT >= 37
