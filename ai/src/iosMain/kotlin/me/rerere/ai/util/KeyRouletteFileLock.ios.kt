package me.rerere.ai.util

import platform.Foundation.NSLock

private val keyRouletteFileLock = NSLock()

internal actual fun <T> withKeyRouletteFileLock(block: () -> T): T {
    keyRouletteFileLock.lock()
    return try {
        block()
    } finally {
        keyRouletteFileLock.unlock()
    }
}
