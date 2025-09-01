package me.rerere.common.utils

import io.pebbletemplates.pebble.PebbleEngine

actual class PlatformPebbleEngine(
    private val engine: PebbleEngine
) {
    actual fun invalidateAll() {
        engine.templateCache.invalidateAll()
    }
}
