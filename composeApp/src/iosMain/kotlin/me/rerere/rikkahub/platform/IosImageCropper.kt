package me.rerere.rikkahub.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

@Composable
public actual fun rememberImageCropper(
    onResult: (ImageCropResult) -> Unit,
): ImageCropper {
    val currentOnResult = rememberUpdatedState(onResult)
    return remember {
        ImageCropper { request ->
            currentOnResult.value(ImageCropResult.Success(request.source))
        }
    }
}
