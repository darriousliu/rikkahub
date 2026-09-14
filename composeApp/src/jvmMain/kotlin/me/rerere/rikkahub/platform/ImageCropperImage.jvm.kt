package me.rerere.rikkahub.platform

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.exif.ExifIFD0Directory
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.withScopedAccess
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.ceil
import kotlin.math.max
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.ImageInfo

internal actual fun readCropImage(source: PlatformFile, maxSize: Int): ImageBitmap = source.withScopedAccess {
    ImageIO.createImageInputStream(source.file).use { stream ->
        val readers = ImageIO.getImageReaders(stream)
        if (!readers.hasNext()) return@withScopedAccess readSkiaCropImage(source.file, maxSize)
        val reader = readers.next()
        try {
            reader.input = stream
            val sample = ceil(max(reader.getWidth(0), reader.getHeight(0)).toDouble() / maxSize).toInt().coerceAtLeast(1)
            val param = reader.defaultReadParam.apply { setSourceSubsampling(sample, sample, 0, 0) }
            val sampled = reader.read(0, param)
            val bitmap = try { sampled.toComposeImageBitmap() } finally { sampled.flush() }
            val orientation = runCatching {
                ImageMetadataReader.readMetadata(source.file).getFirstDirectoryOfType(ExifIFD0Directory::class.java)
                    ?.getInteger(ExifIFD0Directory.TAG_ORIENTATION)
            }.getOrNull() ?: 1
            orientCropImage(bitmap, orientation)
        } finally {
            reader.dispose()
        }
    }
}

// ImageIO handles the common formats with decoder subsampling; Skia supplies WebP and other codecs.
private fun readSkiaCropImage(file: File, maxSize: Int): ImageBitmap = Data.makeFromBytes(file.readBytes()).use { data ->
    Codec.makeFromData(data).use { codec ->
        val info = codec.imageInfo
        val ratio = max(info.width, info.height).toFloat() / maxSize
        val bitmap = Bitmap()
        try {
            val width = if (ratio > 1) (info.width / ratio).toInt().coerceAtLeast(1) else info.width
            val height = if (ratio > 1) (info.height / ratio).toInt().coerceAtLeast(1) else info.height
            bitmap.allocPixels(ImageInfo.makeN32Premul(width, height))
            codec.readPixels(bitmap)
            orientCropImage(bitmap.asComposeImageBitmap(), codec.encodedOrigin.ordinal)
        } catch (error: Exception) {
            bitmap.close()
            throw error
        }
    }
}

internal fun orientCropImage(image: ImageBitmap, orientation: Int): ImageBitmap {
    if (orientation !in 2..8) return image
    val swapped = orientation >= 5
    val result = ImageBitmap(if (swapped) image.height else image.width, if (swapped) image.width else image.height)
    val transform = CropTransform(
        center = Offset(result.width / 2f, result.height / 2f),
        scale = 1f,
        rotation = when (orientation) { 3 -> 180f; 5, 8 -> -90f; 6, 7 -> 90f; else -> 0f },
        flipX = orientation in listOf(2, 5, 7),
        flipY = orientation == 4,
    )
    Canvas(result).drawCropImage(image, transform, Paint())
    return result
}
