package me.rerere.search

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.intl.Locale
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.network.parseGetRequest
import io.ktor.client.plugins.timeout
import io.ktor.client.request.cookie
import io.ktor.client.request.header
import io.ktor.http.encodeURLParameter
import io.ktor.http.userAgent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.search.SearchResult.SearchResultItem
import org.jetbrains.compose.resources.stringResource
import rikkahub.search.generated.resources.Res
import rikkahub.search.generated.resources.bing_desc

object BingSearchService : SearchService<SearchServiceOptions.BingLocalOptions> {
    override val name: String = "Bing"

    @Composable
    override fun Description() {
        Text(stringResource(Res.string.bing_desc))
    }

    override val parameters: InputSchema?
        get() = InputSchema.Obj(
            properties = buildJsonObject {
                put("query", buildJsonObject {
                    put("type", "string")
                    put("description", "search keyword")
                })
            },
            required = listOf("query")
        )

    override val scrapingParameters: InputSchema? = null

    override suspend fun search(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.BingLocalOptions
    ): Result<SearchResult> = withContext(Dispatchers.IO) {
        runCatching {
            val query = params["query"]?.jsonPrimitive?.content ?: error("query is required")
            val url = "https://www.bing.com/search?q=" + query.encodeURLParameter()
            val locale = Locale.current
            val acceptLanguage = "${locale.language}-${locale.region},${locale.language}"
            val doc = Ksoup.parseGetRequest(url)
                userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                header(
                    "Accept",
                    "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8"
                )
                header("Accept-Language", acceptLanguage)
                header("Accept-Encoding", "gzip, deflate, sdch")
                header("Accept-Charset", "utf-8")
                header("Connection", "keep-alive")
                header("Referrer", "https://www.bing.com/")
                cookie("SRCHHPGUSR", "ULSR=1")
                timeout {
                    requestTimeoutMillis = 5000
                }
            }

            // 解析搜索结果
            val results = doc.select("li.b_algo").map { element ->
                val title = element.select("h2").text()
                val link = element.select("h2 > a").attr("href")
                val snippet = element.select(".b_caption p").text()
                SearchResultItem(
                    title = title,
                    url = link,
                    text = snippet
                )
            }

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
