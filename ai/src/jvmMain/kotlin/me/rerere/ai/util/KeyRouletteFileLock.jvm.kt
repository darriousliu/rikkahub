package me.rerere.ai.util

private val keyRouletteFileLock = Any()

internal actual fun <T> withKeyRouletteFileLock(block: () -> T): T = synchronized(keyRouletteFileLock, block)
