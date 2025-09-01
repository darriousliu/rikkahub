package me.rerere.rikkahub.utils

import coil3.request.ImageRequest
import me.rerere.common.PlatformContext

expect fun ImageRequest.Builder.platformAllowHardware(enable: Boolean): ImageRequest.Builder

expect object ImageUtils {
    fun getTavernCharacterMeta(context: PlatformContext, uri: String): Result<String>
    fun decodeQRCodeFromUri(context: PlatformContext, uri: String, maxSize: Int = 1024): String?
}

data class ImageInfo(
    val width: Int,
    val height: Int,
    val mimeType: String?
)
