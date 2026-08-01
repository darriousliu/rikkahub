package me.rerere.rikkahub.platform

import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.yalantis.ucrop.UCrop
import com.yalantis.ucrop.UCropActivity
import io.github.vinceglb.filekit.AndroidFile
import io.github.vinceglb.filekit.PlatformFile
import java.io.File
import kotlin.uuid.Uuid

@Composable
public actual fun rememberImageCropper(
    onResult: (ImageCropResult) -> Unit,
): ImageCropper {
    val context = LocalContext.current
    val currentOnResult = rememberUpdatedState(onResult)
    val outputFile = remember { mutableStateOf<File?>(null) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val cropOutput = outputFile.value
        try {
            when (result.resultCode) {
                Activity.RESULT_OK -> {
                    if (cropOutput == null) {
                        currentOnResult.value(
                            ImageCropResult.Failed("Crop output is unavailable"),
                        )
                    } else {
                        currentOnResult.value(
                            ImageCropResult.Success(PlatformFile(cropOutput)),
                        )
                    }
                }

                UCrop.RESULT_ERROR -> {
                    val error = result.data?.let(UCrop::getError)
                    currentOnResult.value(
                        ImageCropResult.Failed(
                            message = error?.message ?: "Unknown crop error",
                            cause = error,
                        ),
                    )
                }

                else -> currentOnResult.value(ImageCropResult.Cancelled)
            }
        } finally {
            cropOutput?.delete()
            outputFile.value = null
        }
    }

    return remember(context, launcher) {
        ImageCropper { request ->
            val destination = File(context.cacheDir, "crop_output_${Uuid.random()}.jpg")
            outputFile.value = destination
            val options = UCrop.Options().apply {
                setFreeStyleCropEnabled(request.freeStyleCropEnabled)
                setAllowedGestures(
                    UCropActivity.SCALE,
                    UCropActivity.ROTATE,
                    UCropActivity.NONE,
                )
                setCompressionFormat(Bitmap.CompressFormat.PNG)
            }
            var crop = UCrop.of(
                request.source.toAndroidUri(),
                Uri.fromFile(destination),
            ).withOptions(options).withMaxResultSize(
                request.maxWidth,
                request.maxHeight,
            )
            request.aspectRatio?.let { (x, y) ->
                crop = crop.withAspectRatio(x, y)
            }
            launcher.launch(crop.getIntent(context))
        }
    }
}

private fun PlatformFile.toAndroidUri(): Uri = when (val file = androidFile) {
    is AndroidFile.FileWrapper -> Uri.fromFile(file.file)
    is AndroidFile.UriWrapper -> file.uri
}
