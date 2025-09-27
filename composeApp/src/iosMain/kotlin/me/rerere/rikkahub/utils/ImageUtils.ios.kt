package me.rerere.rikkahub.utils

import coil3.request.ImageRequest
import me.rerere.common.PlatformContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

actual object ImageUtils : KoinComponent {
    private val qrCodeDecoder by inject<QRCodeDecoder>()

    actual fun getTavernCharacterMeta(context: PlatformContext, uri: String): Result<String> {
        TODO("Not yet implemented")
    }

    actual fun decodeQRCodeFromUri(context: PlatformContext, uri: String, maxSize: Int): String? {
        return qrCodeDecoder.decode(uri)
    }
}

actual fun ImageRequest.Builder.platformAllowHardware(enable: Boolean): ImageRequest.Builder {
    return this
}
