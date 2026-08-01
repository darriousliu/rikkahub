package me.rerere.rikkahub.ui.components.ui.permission

import androidx.compose.runtime.Composable

data class PermissionInfo(
    val permission: String,
    val displayName: @Composable () -> Unit,
    val usage: @Composable () -> Unit,
    val required: Boolean = false,
)

enum class PermissionStatus {
    NotRequested,
    Granted,
    Denied,
    DeniedPermanently,
}

data class PermissionResult(
    val permission: String,
    val status: PermissionStatus,
    val isGranted: Boolean = status == PermissionStatus.Granted,
)

data class MultiplePermissionResult(
    val results: Map<String, PermissionResult>,
    val allGranted: Boolean = results.values.all { it.isGranted },
    val allRequiredGranted: Boolean,
)

expect val PermissionCamera: PermissionInfo

expect val PermissionRecordAudio: PermissionInfo

expect val PermissionNotification: PermissionInfo

expect val PermissionLocalNetwork: PermissionInfo
