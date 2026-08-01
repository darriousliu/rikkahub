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
        is SearchServiceOptions.OllamaOptions -> OllamaSearchService as SearchService<T>
        is SearchServiceOptions.PerplexityOptions -> PerplexitySearchService as SearchService<T>
        is SearchServiceOptions.FirecrawlOptions -> FirecrawlSearchService as SearchService<T>
        is SearchServiceOptions.JinaOptions -> JinaSearchService as SearchService<T>
        is SearchServiceOptions.BochaOptions -> BochaSearchService as SearchService<T>
        is SearchServiceOptions.GrokOptions -> GrokSearchService as SearchService<T>
        is SearchServiceOptions.TinyfishOptions -> TinyfishSearchService as SearchService<T>
        is SearchServiceOptions.SerperOptions -> SerperSearchService as SearchService<T>
        is SearchServiceOptions.CustomJsOptions -> CustomJsSearchService as SearchService<T>
    }
