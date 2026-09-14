package me.rerere.rikkahub.platform

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.unit.IntSize
import io.github.vinceglb.filekit.PlatformFile
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.impl.use

internal const val CROP_PREVIEW_SIZE = 1536

// The decoder samples before allocating the preview raster. Called on Dispatchers.IO only.
internal expect fun readCropImage(source: PlatformFile, maxSize: Int): ImageBitmap

internal data class CropTransform(
    val center: Offset,
    val scale: Float,
    val rotation: Float = 0f,
    val flipX: Boolean = false,
    val flipY: Boolean = false,
)

internal data class CropSelection(val imageSize: IntSize, val region: Rect, val transform: CropTransform)

@Stable
internal class ImageCropState(val imageSize: IntSize, private val request: ImageCropRequest) {
    var viewport by mutableStateOf(Size.Zero)
        private set
    var region by mutableStateOf(Rect.Zero)
        private set
    var transform by mutableStateOf(CropTransform(Offset.Zero, 1f))
        private set

    val minimumScale: Float
        get() = minimumCropScale(imageSize, region, transform.rotation)

    fun layout(size: Size) {
        if (size != viewport && size.width > 0 && size.height > 0) {
            viewport = size
            reset()
        }
    }

    fun reset() {
        val ratio = request.aspectRatio?.let { it.first / it.second }
            ?: (imageSize.width.toFloat() / imageSize.height)
        val width = min(viewport.width * 0.85f, viewport.height * 0.85f * ratio)
        val size = Size(width, width / ratio)
        region = Rect(Offset((viewport.width - size.width) / 2, (viewport.height - size.height) / 2), size)
        transform = CropTransform(region.center, minimumCropScale(imageSize, region, 0f))
    }

    fun update(value: CropTransform) {
        transform = constrainCropTransform(value, imageSize, region)
    }

    fun mirror(horizontal: Boolean) {
        val delta = transform.center - region.center
        update(transform.copy(
            center = region.center + Offset(if (horizontal) -delta.x else delta.x, if (horizontal) delta.y else -delta.y),
            rotation = -transform.rotation,
            flipX = transform.flipX xor horizontal,
            flipY = transform.flipY xor !horizontal,
        ))
    }

    fun gesture(centroid: Offset, pan: Offset, zoom: Float, rotation: Float) {
        val previous = transform
        val angle = normalizeCropAngle(previous.rotation + rotation)
        val scale = (previous.scale * zoom).coerceIn(
            minimumCropScale(imageSize, region, angle),
            minimumCropScale(imageSize, region, angle) * 8f,
        )
        update(previous.copy(
            center = centroid + pan + ((previous.center - centroid) * (scale / previous.scale)).rotate(rotation),
            scale = scale,
            rotation = angle,
        ))
    }

    fun resize(corner: Int, delta: Offset, minSize: Float) {
        if (!request.freeStyleCropEnabled) return
        val old = region
        val left = corner == 0 || corner == 3
        val top = corner < 2
        val x = if (left) (old.left + delta.x).coerceIn(0f, old.right - min(minSize, old.width))
            else (old.right + delta.x).coerceIn(old.left + min(minSize, old.width), viewport.width)
        val y = if (top) (old.top + delta.y).coerceIn(0f, old.bottom - min(minSize, old.height))
            else (old.bottom + delta.y).coerceIn(old.top + min(minSize, old.height), viewport.height)
        region = Rect(if (left) x else old.left, if (top) y else old.top,
            if (left) old.right else x, if (top) old.bottom else y)
        update(transform)
    }

    fun selection() = CropSelection(imageSize, region, transform)
}

internal fun normalizeCropAngle(degrees: Float): Float = ((degrees + 180f) % 360f + 360f) % 360f - 180f

private fun Offset.rotate(degrees: Float): Offset {
    val radians = degrees * (PI / 180).toFloat()
    val c = cos(radians)
    val s = sin(radians)
    return Offset(x * c - y * s, x * s + y * c)
}

internal fun minimumCropScale(size: IntSize, crop: Rect, degrees: Float): Float {
    val radians = degrees * (PI / 180).toFloat()
    val c = abs(cos(radians))
    val s = abs(sin(radians))
    return max((crop.width * c + crop.height * s) / size.width,
        (crop.width * s + crop.height * c) / size.height)
}

internal fun constrainCropTransform(value: CropTransform, size: IntSize, crop: Rect): CropTransform {
    val scale = value.scale.coerceAtLeast(minimumCropScale(size, crop, value.rotation))
    val radians = value.rotation * (PI / 180).toFloat()
    val c = abs(cos(radians))
    val s = abs(sin(radians))
    val boundX = max(0f, (size.width * scale - crop.width * c - crop.height * s) / 2)
    val boundY = max(0f, (size.height * scale - crop.width * s - crop.height * c) / 2)
    val localPan = (value.center - crop.center).rotate(-value.rotation)
    val center = crop.center + Offset(localPan.x.coerceIn(-boundX, boundX), localPan.y.coerceIn(-boundY, boundY))
        .rotate(value.rotation)
    return value.copy(center = center, scale = scale)
}

// Preview and export use exactly the same transform, including the order of rotation and mirrors.
internal fun Canvas.drawCropImage(image: ImageBitmap, value: CropTransform, paint: Paint) {
    save()
    translate(value.center.x, value.center.y)
    rotate(value.rotation)
    scale(value.scale * if (value.flipX) -1f else 1f, value.scale * if (value.flipY) -1f else 1f)
    drawImage(image, Offset(-image.width / 2f, -image.height / 2f), paint)
    restore()
}

internal fun renderCrop(image: ImageBitmap, selection: CropSelection, maxSize: IntSize): ImageBitmap {
    val crop = selection.region
    val scale = selection.transform.scale * selection.imageSize.width / image.width
    val outputScale = min(1f / scale, min(maxSize.width / crop.width, maxSize.height / crop.height))
    val output = ImageBitmap(max(1, (crop.width * outputScale).roundToInt()),
        max(1, (crop.height * outputScale).roundToInt()))
    Canvas(output).apply {
        scale(output.width / crop.width, output.height / crop.height)
        translate(-crop.left, -crop.top)
        drawCropImage(image, selection.transform.copy(scale = scale), Paint().apply {
            filterQuality = FilterQuality.Medium
        })
    }
    return output
}

internal fun ImageBitmap.encodeCropPng(): ByteArray = Image.makeFromBitmap(asSkiaBitmap()).use { image ->
    checkNotNull(image.encodeToData(EncodedImageFormat.PNG)).use { it.bytes }
}
