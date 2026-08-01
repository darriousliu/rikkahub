package me.rerere.search

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import me.rerere.search.generated.resources.Res
import me.rerere.search.generated.resources.bing_desc
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.search.SearchResult.SearchResultItem
import me.rerere.search.SearchService.Companion.httpClient
import java.util.Locale

object BingSearchService : SearchService<SearchServiceOptions.BingLocalOptions> {
    override val name: String = "Bing"

    @Composable
    override fun Description() {
        Text(stringResource(Res.string.bing_desc))
    }

    override fun parameters(options: SearchServiceOptions.BingLocalOptions): InputSchema? =
        InputSchema.Obj(
            properties = buildJsonObject {
                put("query", buildJsonObject {
                    put("type", "string")
                    put("description", "search keyword")
                })
            },
            required = listOf("query")
        )

    override fun scrapingParameters(options: SearchServiceOptions.BingLocalOptions): InputSchema? = null

    override suspend fun search(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.BingLocalOptions
    ): Result<SearchResult> = withContext(Dispatchers.IO) {
        runCatching {
            val query = params["query"]?.jsonPrimitive?.content ?: error("query is required")
            val url = "https://www.bing.com/search?q=" + encodeSearchQuery(query)
            val locale = Locale.getDefault()
            val acceptLanguage = "${locale.language}-${locale.country},${locale.language}"
            val response = httpClient.executeSearchRequest(
                url = url,
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
                    "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
                    "Accept-Language" to acceptLanguage,
                    "Accept-Charset" to "utf-8",
                    "Connection" to "keep-alive",
                    "Referer" to "https://www.bing.com/",
                    "Cookie" to "SRCHHPGUSR=ULSR=1",
                ),
                timeoutMillis = 5_000,
            )
            val html = response.run {
                check(response.code in 200..399) {
                    "Bing search failed with code ${response.code}: ${response.message}"
                }
                response.body
            }

            val results = parseBingSearchResults(html)

            require(results.isNotEmpty()) {
                "Search failed: no results found"
            }

            SearchResult(items = results)
        }
    }

    override suspend fun scrape(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.BingLocalOptions
    ): Result<ScrapedResult> {
        return Result.failure(Exception("Scraping is not supported for Bing"))
    }
}

private fun parseBingSearchResults(document: Document): List<SearchResultItem> =
    document.select("li.b_algo").map { element ->
        SearchResultItem(
            title = element.select("h2").text(),
            url = element.select("h2 > a").attr("href"),
            text = element.select(".b_caption p").text(),
        )
    }

internal fun parseBingSearchResults(html: String): List<SearchResultItem> =
    parseBingSearchResults(Ksoup.parse(html))
