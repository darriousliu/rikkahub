package me.rerere.common.utils

import android.graphics.BitmapFactory
import coil3.Bitmap

actual fun ByteArray.toPlatformBitmap(): Bitmap {
    return BitmapFactory.decodeByteArray(this, 0, size)
}
