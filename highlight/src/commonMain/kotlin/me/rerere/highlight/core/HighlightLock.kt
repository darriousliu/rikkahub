package me.rerere.highlight.core

internal expect class HighlightLock()

internal expect fun <T> HighlightLock.withLock(block: () -> T): T
