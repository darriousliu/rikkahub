package me.rerere.search

@Suppress("UNCHECKED_CAST")
internal actual fun <T : SearchServiceOptions> platformSearchServiceFor(options: T): SearchService<T> =
    when (options) {
        is SearchServiceOptions.BingLocalOptions -> BingSearchService as SearchService<T>
        else -> error("Search provider is not available on JVM yet: ${options.displayName}")
    }
