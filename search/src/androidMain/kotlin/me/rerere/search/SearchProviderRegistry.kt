package me.rerere.search

import kotlin.reflect.KClass

internal class SearchProviderRegistration<T : SearchServiceOptions>(
    val optionsType: KClass<T>,
    val displayName: String,
    val createOptions: () -> T,
    val service: SearchService<T>,
)

object SearchProviderRegistry {
    private val bing = registration(
        optionsType = SearchServiceOptions.BingLocalOptions::class,
        displayName = "Bing",
        createOptions = SearchServiceOptions::BingLocalOptions,
        service = BingSearchService,
    )
    private val rikkaHub = registration(
        optionsType = SearchServiceOptions.RikkaHubOptions::class,
        displayName = "RikkaHub",
        createOptions = SearchServiceOptions::RikkaHubOptions,
        service = RikkaHubSearchService,
    )
    private val zhipu = registration(
        optionsType = SearchServiceOptions.ZhipuOptions::class,
        displayName = "智谱",
        createOptions = SearchServiceOptions::ZhipuOptions,
        service = ZhipuSearchService,
    )
    private val tavily = registration(
        optionsType = SearchServiceOptions.TavilyOptions::class,
        displayName = "Tavily",
        createOptions = SearchServiceOptions::TavilyOptions,
        service = TavilySearchService,
    )
    private val exa = registration(
        optionsType = SearchServiceOptions.ExaOptions::class,
        displayName = "Exa",
        createOptions = SearchServiceOptions::ExaOptions,
        service = ExaSearchService,
    )
    private val searXng = registration(
        optionsType = SearchServiceOptions.SearXNGOptions::class,
        displayName = "SearXNG",
        createOptions = SearchServiceOptions::SearXNGOptions,
        service = SearXNGService,
    )
    private val linkUp = registration(
        optionsType = SearchServiceOptions.LinkUpOptions::class,
        displayName = "LinkUp",
        createOptions = SearchServiceOptions::LinkUpOptions,
        service = LinkUpService,
    )
    private val brave = registration(
        optionsType = SearchServiceOptions.BraveOptions::class,
        displayName = "Brave",
        createOptions = SearchServiceOptions::BraveOptions,
        service = BraveSearchService,
    )
    private val metaso = registration(
        optionsType = SearchServiceOptions.MetasoOptions::class,
        displayName = "秘塔",
        createOptions = SearchServiceOptions::MetasoOptions,
        service = MetasoSearchService,
    )
    private val ollama = registration(
        optionsType = SearchServiceOptions.OllamaOptions::class,
        displayName = "Ollama",
        createOptions = SearchServiceOptions::OllamaOptions,
        service = OllamaSearchService,
    )
    private val perplexity = registration(
        optionsType = SearchServiceOptions.PerplexityOptions::class,
        displayName = "Perplexity",
        createOptions = SearchServiceOptions::PerplexityOptions,
        service = PerplexitySearchService,
    )
    private val firecrawl = registration(
        optionsType = SearchServiceOptions.FirecrawlOptions::class,
        displayName = "Firecrawl",
        createOptions = SearchServiceOptions::FirecrawlOptions,
        service = FirecrawlSearchService,
    )
    private val jina = registration(
        optionsType = SearchServiceOptions.JinaOptions::class,
        displayName = "Jina",
        createOptions = SearchServiceOptions::JinaOptions,
        service = JinaSearchService,
    )
    private val bocha = registration(
        optionsType = SearchServiceOptions.BochaOptions::class,
        displayName = "博查",
        createOptions = SearchServiceOptions::BochaOptions,
        service = BochaSearchService,
    )
    private val grok = registration(
        optionsType = SearchServiceOptions.GrokOptions::class,
        displayName = "Grok",
        createOptions = SearchServiceOptions::GrokOptions,
        service = GrokSearchService,
    )
    private val tinyfish = registration(
        optionsType = SearchServiceOptions.TinyfishOptions::class,
        displayName = "Tinyfish",
        createOptions = SearchServiceOptions::TinyfishOptions,
        service = TinyfishSearchService,
    )
    private val serper = registration(
        optionsType = SearchServiceOptions.SerperOptions::class,
        displayName = "Serper",
        createOptions = SearchServiceOptions::SerperOptions,
        service = SerperSearchService,
    )
    private val customJs = registration(
        optionsType = SearchServiceOptions.CustomJsOptions::class,
        displayName = "Custom JS",
        createOptions = SearchServiceOptions::CustomJsOptions,
        service = CustomJsSearchService,
    )

    internal val registrations: List<SearchProviderRegistration<out SearchServiceOptions>> = listOf(
        bing,
        rikkaHub,
        zhipu,
        tavily,
        exa,
        searXng,
        linkUp,
        brave,
        metaso,
        ollama,
        perplexity,
        firecrawl,
        jina,
        bocha,
        grok,
        tinyfish,
        serper,
        customJs,
    )

    internal val types: Map<KClass<out SearchServiceOptions>, String> =
        registrations.associate { registration ->
            registration.optionsType to registration.displayName
        }

    fun createOptions(type: KClass<*>): SearchServiceOptions =
        registrations.firstOrNull { registration -> registration.optionsType == type }
            ?.createOptions
            ?.invoke()
            ?: throw IllegalArgumentException("Unsupported search service options type: ${type.qualifiedName}")

    @Suppress("UNCHECKED_CAST")
    fun <T : SearchServiceOptions> serviceFor(options: T): SearchService<T> =
        registrationFor(options).service as SearchService<T>

    private fun registrationFor(
        options: SearchServiceOptions,
    ): SearchProviderRegistration<out SearchServiceOptions> = when (options) {
        is SearchServiceOptions.BingLocalOptions -> bing
        is SearchServiceOptions.RikkaHubOptions -> rikkaHub
        is SearchServiceOptions.ZhipuOptions -> zhipu
        is SearchServiceOptions.TavilyOptions -> tavily
        is SearchServiceOptions.ExaOptions -> exa
        is SearchServiceOptions.SearXNGOptions -> searXng
        is SearchServiceOptions.LinkUpOptions -> linkUp
        is SearchServiceOptions.BraveOptions -> brave
        is SearchServiceOptions.MetasoOptions -> metaso
        is SearchServiceOptions.OllamaOptions -> ollama
        is SearchServiceOptions.PerplexityOptions -> perplexity
        is SearchServiceOptions.FirecrawlOptions -> firecrawl
        is SearchServiceOptions.JinaOptions -> jina
        is SearchServiceOptions.BochaOptions -> bocha
        is SearchServiceOptions.GrokOptions -> grok
        is SearchServiceOptions.TinyfishOptions -> tinyfish
        is SearchServiceOptions.SerperOptions -> serper
        is SearchServiceOptions.CustomJsOptions -> customJs
    }

    private fun <T : SearchServiceOptions> registration(
        optionsType: KClass<T>,
        displayName: String,
        createOptions: () -> T,
        service: SearchService<T>,
    ): SearchProviderRegistration<T> = SearchProviderRegistration(
        optionsType = optionsType,
        displayName = displayName,
        createOptions = createOptions,
        service = service,
    )
}
