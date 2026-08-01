package me.rerere.rikkahub.platform

import android.os.Build
import coil3.ComponentRegistry
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder

public actual val platformGifDecodingMode: GifDecodingMode = GifDecodingMode.ANIMATED

public actual fun ComponentRegistry.Builder.addPlatformGifDecoder() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        add(AnimatedImageDecoder.Factory())
    } else {
        add(GifDecoder.Factory())
    }
}
