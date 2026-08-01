package me.rerere.search

@Suppress("UNCHECKED_CAST")
internal actual fun <T : SearchServiceOptions> platformSearchServiceFor(options: T): SearchService<T> =
    when (options) {
        is SearchServiceOptions.BingLocalOptions -> BingSearchService
        is SearchServiceOptions.RikkaHubOptions -> RikkaHubSearchService
        is SearchServiceOptions.ZhipuOptions -> ZhipuSearchService
        is SearchServiceOptions.TavilyOptions -> TavilySearchService
        is SearchServiceOptions.ExaOptions -> ExaSearchService
        is SearchServiceOptions.SearXNGOptions -> SearXNGService
        is SearchServiceOptions.LinkUpOptions -> LinkUpService
        is SearchServiceOptions.BraveOptions -> BraveSearchService
        is SearchServiceOptions.MetasoOptions -> MetasoSearchService
        is SearchServiceOptions.OllamaOptions -> OllamaSearchService
        is SearchServiceOptions.PerplexityOptions -> PerplexitySearchService
        is SearchServiceOptions.FirecrawlOptions -> FirecrawlSearchService
        is SearchServiceOptions.JinaOptions -> JinaSearchService
        is SearchServiceOptions.BochaOptions -> BochaSearchService
        is SearchServiceOptions.GrokOptions -> GrokSearchService
        is SearchServiceOptions.TinyfishOptions -> TinyfishSearchService
        is SearchServiceOptions.SerperOptions -> SerperSearchService
        is SearchServiceOptions.CustomJsOptions -> CustomJsSearchService
    } as SearchService<T>
