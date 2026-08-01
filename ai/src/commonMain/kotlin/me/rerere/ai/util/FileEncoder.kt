package me.rerere.ai.util

import me.rerere.ai.ui.UIMessagePart

data class EncodedImage(
    val base64: String,
    val mimeType: String,
)

internal data class LocalFilePayload(
    val name: String,
    val extension: String,
    val bytes: ByteArray,
)

fun UIMessagePart.Image.encodeBase64(withPrefix: Boolean = true): Result<EncodedImage> = runCatching {
    when {
        url.startsWith("file://") -> encodeLocalImageFile(url, withPrefix)
        url.startsWith("data:") -> EncodedImage(
            base64 = url,
            mimeType = url.substringAfter("data:").substringBefore(';'),
        )
        url.startsWith("http") -> EncodedImage(base64 = url, mimeType = "image/png")
        else -> throw IllegalArgumentException("Unsupported URL format: $url")
    }
}

fun UIMessagePart.Video.encodeBase64(withPrefix: Boolean = true): Result<String> = runCatching {
    if (!url.startsWith("file://")) throw IllegalArgumentException("Unsupported URL format: $url")
    encodeLocalMediaFile(url, mimeType = "video/mp4", withPrefix = withPrefix)
}

fun UIMessagePart.Audio.encodeBase64(withPrefix: Boolean = true): Result<String> = runCatching {
    if (!url.startsWith("file://")) throw IllegalArgumentException("Unsupported URL format: $url")
    encodeLocalMediaFile(url, mimeType = "audio/mp3", withPrefix = withPrefix)
}

internal expect fun encodeLocalImageFile(fileUrl: String, withPrefix: Boolean): EncodedImage

internal expect fun encodeLocalMediaFile(
    fileUrl: String,
    mimeType: String,
    withPrefix: Boolean,
): String

internal expect fun readLocalFile(pathOrUrl: String): LocalFilePayload
