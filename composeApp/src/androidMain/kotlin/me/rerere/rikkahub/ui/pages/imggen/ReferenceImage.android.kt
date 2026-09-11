package me.rerere.rikkahub.ui.pages.imggen

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.readBytes
import java.io.ByteArrayOutputStream

internal actual suspend fun readReferenceImage(source: PlatformFile): ByteArray {
    val bytes = source.readBytes()
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
        var sampleSize = 1
        while (bounds.outHeight / 2 / sampleSize >= 2048 && bounds.outWidth / 2 / sampleSize >= 2048) {
            sampleSize *= 2
        }
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.RGB_565
    }) ?: error("Failed to decode image")
    return try {
        ByteArrayOutputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            it.toByteArray()
        }
    } finally {
        bitmap.recycle()
    }
}
