package me.rerere.search

import kotlin.reflect.KClass

internal class SearchProviderRegistration<T : SearchServiceOptions>(
    val optionsType: KClass<T>,
    val displayName: String,
    val createOptions: () -> T,
)

object SearchProviderRegistry {
    private val bing = registration(
        optionsType = SearchServiceOptions.BingLocalOptions::class,
        displayName = "Bing",
        createOptions = SearchServiceOptions::BingLocalOptions,
    )
    private val rikkaHub = registration(
        optionsType = SearchServiceOptions.RikkaHubOptions::class,
        displayName = "RikkaHub",
        createOptions = SearchServiceOptions::RikkaHubOptions,
    )
    private val zhipu = registration(
        optionsType = SearchServiceOptions.ZhipuOptions::class,
        displayName = "智谱",
        createOptions = SearchServiceOptions::ZhipuOptions,
    )
    private val tavily = registration(
        optionsType = SearchServiceOptions.TavilyOptions::class,
        displayName = "Tavily",
        createOptions = SearchServiceOptions::TavilyOptions,
    )
    private val exa = registration(
        optionsType = SearchServiceOptions.ExaOptions::class,
        displayName = "Exa",
        createOptions = SearchServiceOptions::ExaOptions,
    )
    private val searXng = registration(
        optionsType = SearchServiceOptions.SearXNGOptions::class,
        displayName = "SearXNG",
        createOptions = SearchServiceOptions::SearXNGOptions,
    )
    private val linkUp = registration(
        optionsType = SearchServiceOptions.LinkUpOptions::class,
        displayName = "LinkUp",
        createOptions = SearchServiceOptions::LinkUpOptions,
    )
    private val brave = registration(
        optionsType = SearchServiceOptions.BraveOptions::class,
        displayName = "Brave",
        createOptions = SearchServiceOptions::BraveOptions,
    )
    private val metaso = registration(
        optionsType = SearchServiceOptions.MetasoOptions::class,
        displayName = "秘塔",
        createOptions = SearchServiceOptions::MetasoOptions,
    )
    private val ollama = registration(
        optionsType = SearchServiceOptions.OllamaOptions::class,
        displayName = "Ollama",
        createOptions = SearchServiceOptions::OllamaOptions,
    )
    private val perplexity = registration(
        optionsType = SearchServiceOptions.PerplexityOptions::class,
        displayName = "Perplexity",
        createOptions = SearchServiceOptions::PerplexityOptions,
    )
    private val firecrawl = registration(
        optionsType = SearchServiceOptions.FirecrawlOptions::class,
        displayName = "Firecrawl",
        createOptions = SearchServiceOptions::FirecrawlOptions,
    )
    private val jina = registration(
        optionsType = SearchServiceOptions.JinaOptions::class,
        displayName = "Jina",
        createOptions = SearchServiceOptions::JinaOptions,
    )
    private val bocha = registration(
        optionsType = SearchServiceOptions.BochaOptions::class,
        displayName = "博查",
        createOptions = SearchServiceOptions::BochaOptions,
    )
    private val grok = registration(
        optionsType = SearchServiceOptions.GrokOptions::class,
        displayName = "Grok",
        createOptions = SearchServiceOptions::GrokOptions,
    )
    private val tinyfish = registration(
        optionsType = SearchServiceOptions.TinyfishOptions::class,
        displayName = "Tinyfish",
        createOptions = SearchServiceOptions::TinyfishOptions,
    )
    private val serper = registration(
        optionsType = SearchServiceOptions.SerperOptions::class,
        displayName = "Serper",
        createOptions = SearchServiceOptions::SerperOptions,
    )
    private val customJs = registration(
        optionsType = SearchServiceOptions.CustomJsOptions::class,
        displayName = "Custom JS",
        createOptions = SearchServiceOptions::CustomJsOptions,
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
        platformSearchServiceFor(options)

    private fun <T : SearchServiceOptions> registration(
        optionsType: KClass<T>,
        displayName: String,
        createOptions: () -> T,
    ): SearchProviderRegistration<T> = SearchProviderRegistration(
        optionsType = optionsType,
        displayName = displayName,
        createOptions = createOptions,
    )
}

internal expect fun <T : SearchServiceOptions> platformSearchServiceFor(options: T): SearchService<T>
