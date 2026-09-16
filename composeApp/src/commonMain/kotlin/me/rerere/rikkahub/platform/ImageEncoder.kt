package me.rerere.rikkahub.platform

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Re-encodes an arbitrary image to PNG so every stored attachment shares one format.
 *
 * Returns null when the bytes cannot be decoded as an image.
 */
internal expect suspend fun encodeImageToPng(bytes: ByteArray): ByteArray?

internal expect fun encodeImageBitmapToPng(bitmap: ImageBitmap): ByteArray
