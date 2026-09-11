package me.rerere.rikkahub.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.launch

@Composable
public actual fun rememberImageCropper(
    onResult: suspend (ImageCropResult) -> Unit,
): ImageCropper {
    val currentOnResult = rememberUpdatedState(onResult)
    val scope = rememberCoroutineScope()
    return remember(scope) {
        ImageCropper { request ->
            scope.launch {
                currentOnResult.value(ImageCropResult.Success(request.source))
            }
        }
    }
}
