package me.rerere.rikkahub.ui.components.ai

import android.net.Uri
import androidx.compose.runtime.Composable
import com.dokar.sonner.ToastType
import io.github.vinceglb.filekit.AndroidFile
import io.github.vinceglb.filekit.PlatformFile
import me.rerere.common.logging.Logging
import me.rerere.rikkahub.platform.ImageCropRequest
import me.rerere.rikkahub.platform.ImageCropResult
import me.rerere.rikkahub.platform.rememberImageCropper
import me.rerere.rikkahub.ui.context.LocalToaster

@Composable
internal fun useCropLauncher(
    onCroppedImageReady: (Uri) -> Unit,
    onCleanup: (() -> Unit)? = null,
    aspectRatio: Pair<Float, Float>? = null,
    freeStyleCropEnabled: Boolean = true
): (Uri) -> Unit {
    val toaster = LocalToaster.current
    val cropper = rememberImageCropper { result ->
        when (result) {
            is ImageCropResult.Success -> onCroppedImageReady(result.file.toAndroidUri())
            is ImageCropResult.Failed -> {
                Logging.log(
                    "CropLauncher",
                    "crop failed: ${result.message} | ${result.cause?.stackTraceToString()}"
                )
                toaster.show(
                    "Failed to crop image: ${result.message}",
                    type = ToastType.Error
                )
            }

            ImageCropResult.Cancelled -> Unit
        }
        onCleanup?.invoke()
    }

    return { sourceUri ->
        cropper.launch(
            ImageCropRequest(
                source = PlatformFile(sourceUri),
                aspectRatio = aspectRatio,
                freeStyleCropEnabled = freeStyleCropEnabled,
            )
        )
    }
}

private fun PlatformFile.toAndroidUri(): Uri = when (val file = androidFile) {
    is AndroidFile.FileWrapper -> Uri.fromFile(file.file)
    is AndroidFile.UriWrapper -> file.uri
}
