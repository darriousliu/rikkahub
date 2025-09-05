package me.rerere.ai.util

import me.rerere.ai.ui.UIMessagePart

internal val supportedTypes = setOf(
    "image/jpeg",
    "image/png",
    "image/gif",
    "image/webp",
)

private const val TAG = "FileEncoder"

expect fun UIMessagePart.Image.encodeBase64(withPrefix: Boolean = true): Result<String>
