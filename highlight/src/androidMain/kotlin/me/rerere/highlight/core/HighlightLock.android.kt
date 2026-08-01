package me.rerere.highlight.core

internal actual class HighlightLock actual constructor() {
    internal val monitor = Any()
}

internal actual fun <T> HighlightLock.withLock(block: () -> T): T = synchronized(monitor, block)
