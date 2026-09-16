package me.rerere.rikkahub.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.impl.use

internal actual fun encodeImageBitmapToPng(bitmap: ImageBitmap): ByteArray =
    Image.makeFromBitmap(bitmap.asSkiaBitmap()).use { image ->
        checkNotNull(image.encodeToData(EncodedImageFormat.PNG)).use { it.bytes }
    }
