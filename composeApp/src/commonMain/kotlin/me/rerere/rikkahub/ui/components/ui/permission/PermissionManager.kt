package me.rerere.rikkahub.ui.components.ui.permission

import androidx.compose.runtime.Composable

/**
 * 权限管理器组件
 * 根据平台权限状态自动显示或隐藏权限请求说明。
 *
 * 使用方式：
 * ```
 * val permissionState = rememberPermissionState(permissions)
 *
 * PermissionManager(permissionState = permissionState) {
 *     // 你的UI内容
 *     YourContent()
 * }
 * ```
 */
@Composable
fun PermissionManager(
    permissionState: PermissionState,
    content: @Composable () -> Unit = {},
) {
    // 显示权限请求说明对话框
    if (permissionState.showRationaleDialog && permissionState.currentRationalePermissions.isNotEmpty()) {
        PermissionRationaleDialog(
            permissions = permissionState.currentRationalePermissions,
            permanentlyDeniedPermissions = permissionState.permanentlyDeniedPermissions,
            onProceed = {
                permissionState.proceedFromRationale()
            },
            onCancel = {
                permissionState.cancelPermissionRequest()
            }
        )
    }

    // 主要内容
    content()
}
