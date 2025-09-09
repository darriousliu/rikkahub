package me.rerere.common.utils

import io.pebbletemplates.pebble.PebbleEngine

actual class PlatformPebbleEngine(
    private val engine: PebbleEngine = PebbleEngine.Builder().build()
) {
    actual fun invalidateAll() {
        engine.templateCache.invalidateAll()
    }
}
