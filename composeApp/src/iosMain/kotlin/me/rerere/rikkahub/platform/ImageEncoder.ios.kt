package me.rerere.rikkahub.platform

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation
import platform.posix.memcpy

@OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
internal actual suspend fun encodeImageToPng(bytes: ByteArray): ByteArray? {
    val source = memScoped {
        NSData.create(bytes = allocArrayOf(bytes), length = bytes.size.toULong())
    }
    val image = UIImage.imageWithData(source) ?: return null
    val png = UIImagePNGRepresentation(image) ?: return null
    val length = png.length.toInt()
    if (length == 0) return null
    val output = ByteArray(length)
    output.usePinned { pinned ->
        memcpy(pinned.addressOf(0), png.bytes, png.length)
    }
    return output
}
