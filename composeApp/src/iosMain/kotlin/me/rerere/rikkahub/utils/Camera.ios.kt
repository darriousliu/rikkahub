package me.rerere.rikkahub.utils

import androidx.compose.runtime.Composable
import com.dokar.sonner.ToastType
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.compose.rememberCameraPickerLauncher
import io.github.vinceglb.filekit.path
import kotlinx.io.files.Path
import me.rerere.rikkahub.ui.components.ui.permission.PermissionCamera
import me.rerere.rikkahub.ui.components.ui.permission.PermissionManager
import me.rerere.rikkahub.ui.components.ui.permission.rememberPermissionState
import me.rerere.rikkahub.ui.context.LocalToaster

@Composable
internal actual fun rememberCameraLauncher(
    onCaptured: (PlatformFile, cleanup: () -> Unit) -> Unit,
): (() -> Unit)? {
    val toaster = LocalToaster.current
    val cameraPermission = rememberPermissionState(PermissionCamera)
    PermissionManager(permissionState = cameraPermission)
    val cameraLauncher = rememberCameraPickerLauncher(
        onError = { error -> toaster.show(error.message.orEmpty(), type = ToastType.Error) },
        onResult = { file ->
            if (file != null) {
                onCaptured(file) { Path(file.path).delete() }
            }
        },
    )
    return {
        if (cameraPermission.allRequiredPermissionsGranted) {
            cameraLauncher.launch()
        } else {
            cameraPermission.requestPermissions()
        }
    }
}
