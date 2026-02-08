package me.rerere.rikkahub.utils

import coil3.request.ImageRequest
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import me.rerere.common.PlatformContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import platform.Foundation.NSData
import platform.Foundation.dataWithBytes
import platform.Foundation.getBytes
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation

actual object ImageUtils : KoinComponent {
    private val qrCodeDecoder by inject<QRCodeDecoder>()

    actual fun getTavernCharacterMeta(context: PlatformContext, uri: String): Result<String> {
        TODO("Not yet implemented")
    }

    actual fun decodeQRCodeFromUri(context: PlatformContext, uri: String, maxSize: Int): String? {
        return qrCodeDecoder.decode(uri)
    }

    actual fun compressToPng(data: ByteArray): ByteArray {
        val nsData = data.usePinned { pinned ->
            NSData.dataWithBytes(pinned.addressOf(0), data.size.toULong())
        }
        val uiImage = UIImage.imageWithData(nsData)
            ?: throw IllegalArgumentException("Cannot decode image bytes on iOS")
        val pngData = UIImagePNGRepresentation(uiImage)
            ?: throw IllegalArgumentException("Cannot encode image as PNG on iOS")
        return ByteArray(pngData.length.toInt()).also { bytes ->
            bytes.usePinned { pinned ->
                pngData.getBytes(pinned.addressOf(0), pngData.length)
            }
        }
    }
}

actual fun ImageRequest.Builder.platformAllowHardware(enable: Boolean): ImageRequest.Builder {
    return this
}
