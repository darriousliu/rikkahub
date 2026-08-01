package me.rerere.search

@Suppress("UNCHECKED_CAST")
internal actual fun <T : SearchServiceOptions> platformSearchServiceFor(options: T): SearchService<T> =
    when (options) {
        is SearchServiceOptions.BingLocalOptions -> BingSearchService as SearchService<T>
        is SearchServiceOptions.RikkaHubOptions -> RikkaHubSearchService as SearchService<T>
        is SearchServiceOptions.ZhipuOptions -> ZhipuSearchService as SearchService<T>
        is SearchServiceOptions.TavilyOptions -> TavilySearchService as SearchService<T>
        is SearchServiceOptions.ExaOptions -> ExaSearchService as SearchService<T>
        is SearchServiceOptions.SearXNGOptions -> SearXNGService as SearchService<T>
        is SearchServiceOptions.LinkUpOptions -> LinkUpService as SearchService<T>
        is SearchServiceOptions.BraveOptions -> BraveSearchService as SearchService<T>
        is SearchServiceOptions.MetasoOptions -> MetasoSearchService as SearchService<T>
        else -> error("Search provider is not available on iOS yet: ${options.displayName}")
    }
