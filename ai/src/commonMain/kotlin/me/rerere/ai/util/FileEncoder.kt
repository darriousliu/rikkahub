package me.rerere.ai.util

import me.rerere.ai.ui.UIMessagePart

internal val supportedTypes = setOf(
    "image/jpeg",
    "image/png",
    "image/gif",
    "image/webp",
)

private const val TAG = "FileEncoder"

data class EncodedImage(
    val base64: String,
    val mimeType: String
)

expect fun UIMessagePart.Image.encodeBase64(withPrefix: Boolean = true): Result<EncodedImage>

expect fun UIMessagePart.Video.encodeBase64(withPrefix: Boolean = true): Result<String>

expect fun UIMessagePart.Audio.encodeBase64(withPrefix: Boolean = true): Result<String>
