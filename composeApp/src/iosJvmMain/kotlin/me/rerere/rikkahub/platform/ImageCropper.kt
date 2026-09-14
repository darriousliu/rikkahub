package me.rerere.rikkahub.platform

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.cacheDir
import io.github.vinceglb.filekit.delete
import io.github.vinceglb.filekit.div
import io.github.vinceglb.filekit.write
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
public actual fun rememberImageCropper(onResult: suspend (ImageCropResult) -> Unit): ImageCropper {
    val currentOnResult = rememberUpdatedState(onResult)
    val scope = rememberCoroutineScope()
    var request by remember { mutableStateOf<ImageCropRequest?>(null) }
    var saving by remember { mutableStateOf(false) }

    request?.let { activeRequest ->
        ImageCropDialog(
            request = activeRequest,
            saving = saving,
            onFinish = { result ->
                request = null
                scope.launch { currentOnResult.value(result) }
            },
            onConfirm = { selection ->
                saving = true
                scope.launch {
                    var output: PlatformFile? = null
                    try {
                        val result = try {
                            withContext(Dispatchers.IO) {
                                val image = readCropImage(activeRequest.source,
                                    max(activeRequest.maxWidth, activeRequest.maxHeight))
                                val cropped = renderCrop(image, selection,
                                    IntSize(activeRequest.maxWidth, activeRequest.maxHeight))
                                val file = FileKit.cacheDir / "crop_output_${Uuid.random()}.png"
                                output = file
                                file.write(cropped.encodeCropPng())
                                ImageCropResult.Success(file)
                            }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            ImageCropResult.Failed(error.message ?: "Unable to crop image", error)
                        }
                        request = null
                        currentOnResult.value(result)
                    } finally {
                        withContext(NonCancellable) { output?.delete(mustExist = false) }
                        saving = false
                    }
                }
            },
        )
    }
    return remember { ImageCropper { request = it } }
}

@Composable
private fun ImageCropDialog(
    request: ImageCropRequest,
    saving: Boolean,
    onFinish: (ImageCropResult) -> Unit,
    onConfirm: (CropSelection) -> Unit,
) {
    var image by remember(request) { mutableStateOf<ImageBitmap?>(null) }
    val currentOnFinish by rememberUpdatedState(onFinish)
    LaunchedEffect(request) {
        try {
            image = withContext(Dispatchers.IO) {
                readCropImage(request.source, CROP_PREVIEW_SIZE).also {
                    // Skia can share immutable pixels instead of copying the bitmap on every drawImage.
                    it.asSkiaBitmap().setImmutable()
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            currentOnFinish(ImageCropResult.Failed(error.message ?: "Unable to read image", error))
        }
    }
    val state = remember(image, request) {
        image?.let { ImageCropState(IntSize(it.width, it.height), request) }
    }
    val cancel = { if (!saving) onFinish(ImageCropResult.Cancelled) }
    Dialog(onDismissRequest = cancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.padding(12.dp).widthIn(max = 900.dp).fillMaxWidth().fillMaxHeight(0.92f),
            shape = MaterialTheme.shapes.large) {
            Column {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = cancel, enabled = !saving) { Text("取消") }
                    Text("裁剪图片", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { state?.let { onConfirm(it.selection()) } },
                        enabled = state != null && state.region.width > 0 && !saving) { Text("确认") }
                }
                Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black), contentAlignment = Alignment.Center) {
                    val preview = image
                    if (preview != null && state != null) {
                        CropPreview(preview, state, request.freeStyleCropEnabled, enabled = !saving)
                    }
                    if (preview == null || saving) CircularProgressIndicator()
                }
                if (state != null) CropControls(state, enabled = !saving)
            }
        }
    }
}

@Composable
private fun CropPreview(image: ImageBitmap, state: ImageCropState, freeStyle: Boolean, enabled: Boolean) {
    val paint = remember { Paint().apply { filterQuality = FilterQuality.Low } }
    val handleSize = 40.dp
    val handlePx = with(LocalDensity.current) { handleSize.toPx() }
    Box(Modifier.fillMaxSize().clipToBounds()
        .onSizeChanged { state.layout(Size(it.width.toFloat(), it.height.toFloat())) }) {
        Canvas(Modifier.fillMaxSize().semantics { contentDescription = "裁剪预览" }
            .pointerInput(state, enabled) {
                if (enabled) detectTransformGestures { centroid, pan, zoom, rotation ->
                    state.gesture(centroid, pan, zoom, rotation)
                }
            }) {
            // Only this drawing phase reads gesture state; the sampled bitmap remains unchanged.
            drawIntoCanvas { it.drawCropImage(image, state.transform, paint) }
            val crop = state.region
            val shade = Color.Black.copy(alpha = 0.6f)
            drawRect(shade, size = Size(size.width, crop.top))
            drawRect(shade, Offset(0f, crop.bottom), Size(size.width, size.height - crop.bottom))
            drawRect(shade, Offset(0f, crop.top), Size(crop.left, crop.height))
            drawRect(shade, Offset(crop.right, crop.top), Size(size.width - crop.right, crop.height))
            drawRect(Color.White, crop.topLeft, crop.size, style = Stroke(1.dp.toPx()))
            for (i in 1..2) {
                val x = crop.left + crop.width * i / 3
                val y = crop.top + crop.height * i / 3
                drawLine(Color.White.copy(alpha = 0.4f), Offset(x, crop.top), Offset(x, crop.bottom))
                drawLine(Color.White.copy(alpha = 0.4f), Offset(crop.left, y), Offset(crop.right, y))
            }
        }
        if (freeStyle) repeat(4) { corner ->
            Canvas(Modifier.offset {
                val crop = state.region
                val x = if (corner == 0 || corner == 3) crop.left else crop.right
                val y = if (corner < 2) crop.top else crop.bottom
                IntOffset((x - handlePx / 2).roundToInt(), (y - handlePx / 2).roundToInt())
            }.size(handleSize).semantics { contentDescription = "裁剪角 ${corner + 1}" }
                .pointerInput(state, enabled) {
                    if (enabled) detectDragGestures { change, delta ->
                        change.consume()
                        state.resize(corner, delta, handlePx)
                    }
                }) {
                drawCircle(Color.White, 4.dp.toPx())
            }
        }
    }
}

@Composable
private fun CropControls(state: ImageCropState, enabled: Boolean) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("缩放", modifier = Modifier.padding(end = 12.dp))
            Slider(value = (state.transform.scale / state.minimumScale).coerceIn(1f, 8f),
                onValueChange = { state.update(state.transform.copy(scale = it * state.minimumScale)) },
                modifier = Modifier.weight(1f).semantics { contentDescription = "缩放" },
                valueRange = 1f..8f, enabled = enabled)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("旋转", modifier = Modifier.padding(end = 12.dp))
            Slider(value = state.transform.rotation,
                onValueChange = { state.update(state.transform.copy(rotation = it)) },
                modifier = Modifier.weight(1f).semantics { contentDescription = "旋转" },
                valueRange = -180f..180f, enabled = enabled)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            TextButton(enabled = enabled, onClick = {
                state.update(state.transform.copy(rotation = normalizeCropAngle(state.transform.rotation + 90f)))
            }) { Text("转 90°") }
            TextButton(enabled = enabled, onClick = { state.mirror(horizontal = true) }) { Text("水平镜像") }
            TextButton(enabled = enabled, onClick = { state.mirror(horizontal = false) }) { Text("垂直镜像") }
            TextButton(enabled = enabled, onClick = state::reset) { Text("重置") }
        }
    }
}
