package me.rerere.rikkahub.platform

import coil3.ComponentRegistry

public enum class GifDecodingMode {
    ANIMATED,
    FIRST_FRAME,
}

public expect val platformGifDecodingMode: GifDecodingMode

public expect fun ComponentRegistry.Builder.addPlatformGifDecoder()
