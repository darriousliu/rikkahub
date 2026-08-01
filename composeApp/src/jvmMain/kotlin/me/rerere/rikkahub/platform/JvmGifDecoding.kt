package me.rerere.rikkahub.platform

import coil3.ComponentRegistry

public actual val platformGifDecodingMode: GifDecodingMode = GifDecodingMode.FIRST_FRAME

public actual fun ComponentRegistry.Builder.addPlatformGifDecoder() = Unit
