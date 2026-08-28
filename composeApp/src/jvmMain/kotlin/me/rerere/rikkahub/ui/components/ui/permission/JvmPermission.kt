package me.rerere.rikkahub.ui.components.ui.permission

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import me.rerere.rikkahub.generated.resources.Res
import me.rerere.rikkahub.generated.resources.permission_camera
import me.rerere.rikkahub.generated.resources.permission_camera_desc
import me.rerere.rikkahub.generated.resources.permission_local_network
import me.rerere.rikkahub.generated.resources.permission_local_network_desc
import me.rerere.rikkahub.generated.resources.permission_microphone
import me.rerere.rikkahub.generated.resources.permission_microphone_desc
import me.rerere.rikkahub.generated.resources.permission_notification
import me.rerere.rikkahub.generated.resources.permission_notification_desc
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

actual val PermissionCamera: PermissionInfo = passivePermission(
    id = "camera",
    displayName = Res.string.permission_camera,
    usage = Res.string.permission_camera_desc,
)

actual val PermissionRecordAudio: PermissionInfo = passivePermission(
    id = "record_audio",
    displayName = Res.string.permission_microphone,
    usage = Res.string.permission_microphone_desc,
)

actual val PermissionNotification: PermissionInfo = passivePermission(
    id = "notifications",
    displayName = Res.string.permission_notification,
    usage = Res.string.permission_notification_desc,
)

actual val RuntimeNotificationPermissionRequired: Boolean = false

actual val PermissionLocalNetwork: PermissionInfo = passivePermission(
    id = "local_network",
    displayName = Res.string.permission_local_network,
    usage = Res.string.permission_local_network_desc,
)

actual val RuntimeLocalNetworkPermissionRequired: Boolean = false

/** The desktop platform grants these without a runtime prompt. */
private fun passivePermission(
    id: String,
    displayName: StringResource,
    usage: StringResource,
): PermissionInfo = PermissionInfo(
    permission = id,
    displayName = { Text(stringResource(displayName)) },
    usage = { Text(stringResource(usage)) },
    required = true,
)

@Composable
actual fun rememberPermissionState(permissions: Set<PermissionInfo>): PermissionState =
    remember(permissions) { GrantedPermissionState() }
