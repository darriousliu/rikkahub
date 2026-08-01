package me.rerere.rikkahub.ui.components.ui.permission

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable

@Stable
interface PermissionState {
    val showRationaleDialog: Boolean
    val currentRationalePermissions: List<PermissionInfo>
    val permanentlyDeniedPermissions: List<PermissionInfo>
    val allPermissionsGranted: Boolean
    val allRequiredPermissionsGranted: Boolean

    fun requestPermissions()

    fun proceedFromRationale()

    fun cancelPermissionRequest()

    fun openAppSettings()
}

@Composable
expect fun rememberPermissionState(permissions: Set<PermissionInfo>): PermissionState

@Composable
fun rememberPermissionState(
    permission: String,
    displayName: @Composable () -> Unit,
    usage: @Composable () -> Unit,
    required: Boolean = false,
): PermissionState = rememberPermissionState(
    permissions = setOf(
        PermissionInfo(
            permission = permission,
            displayName = displayName,
            usage = usage,
            required = required,
        )
    )
)

@Composable
fun rememberPermissionState(info: PermissionInfo): PermissionState =
    rememberPermissionState(permissions = setOf(info))

internal class GrantedPermissionState : PermissionState {
    override val showRationaleDialog: Boolean = false
    override val currentRationalePermissions: List<PermissionInfo> = emptyList()
    override val permanentlyDeniedPermissions: List<PermissionInfo> = emptyList()
    override val allPermissionsGranted: Boolean = true
    override val allRequiredPermissionsGranted: Boolean = true

    override fun requestPermissions() = Unit

    override fun proceedFromRationale() = Unit

    override fun cancelPermissionRequest() = Unit

    override fun openAppSettings() = Unit
}
