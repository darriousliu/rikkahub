package me.rerere.search

internal actual fun <T : SearchServiceOptions> platformSearchServiceFor(options: T): SearchService<T> =
    error("Search provider is not available on JVM yet: ${options.displayName}")
