package me.rerere.common.utils

import org.jetbrains.skia.Bitmap

actual fun ByteArray.toPlatformBitmap(): Bitmap {
    val bitmap = Bitmap()
    bitmap.installPixels(this)
    return bitmap
}
