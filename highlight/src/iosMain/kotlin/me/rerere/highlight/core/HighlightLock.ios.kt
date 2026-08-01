package me.rerere.highlight.core

import platform.Foundation.NSLock

internal actual class HighlightLock actual constructor() {
    internal val lock = NSLock()
}

internal actual fun <T> HighlightLock.withLock(block: () -> T): T {
    lock.lock()
    return try {
        block()
    } finally {
        lock.unlock()
    }
}
