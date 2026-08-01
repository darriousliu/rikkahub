package me.rerere.rikkahub.ui.components.ui.permission

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

actual val PermissionCamera: PermissionInfo = passivePermission("camera", "Camera")
actual val PermissionRecordAudio: PermissionInfo = passivePermission("record_audio", "Microphone")
actual val PermissionNotification: PermissionInfo = passivePermission("notifications", "Notifications")
actual val RuntimeNotificationPermissionRequired: Boolean = false
actual val PermissionLocalNetwork: PermissionInfo = passivePermission("local_network", "Local network")
actual val RuntimeLocalNetworkPermissionRequired: Boolean = false

private fun passivePermission(id: String, label: String): PermissionInfo = PermissionInfo(
    permission = id,
    displayName = { Text(label) },
    usage = { Text("Managed by iOS") },
    required = true,
)

@Composable
actual fun rememberPermissionState(permissions: Set<PermissionInfo>): PermissionState =
    remember(permissions) { GrantedPermissionState() }
