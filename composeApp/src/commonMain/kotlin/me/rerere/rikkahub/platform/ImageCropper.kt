package me.rerere.rikkahub.platform

import androidx.compose.runtime.Composable
import io.github.vinceglb.filekit.PlatformFile

public data class ImageCropRequest(
    val source: PlatformFile,
    val aspectRatio: Pair<Float, Float>? = null,
    val freeStyleCropEnabled: Boolean = true,
    val maxWidth: Int = 4096,
    val maxHeight: Int = 4096,
)

public sealed interface ImageCropResult {
    public data class Success(val file: PlatformFile) : ImageCropResult

    public data object Cancelled : ImageCropResult

    public data class Failed(
        val message: String,
        val cause: Throwable? = null,
    ) : ImageCropResult
}

public fun interface ImageCropper {
    public fun launch(request: ImageCropRequest)
}

@Composable
public expect fun rememberImageCropper(
    onResult: (ImageCropResult) -> Unit,
): ImageCropper
