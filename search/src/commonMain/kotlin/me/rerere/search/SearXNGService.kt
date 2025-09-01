package me.rerere.search

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import co.touchlab.kermit.Logger
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import io.ktor.util.encodeBase64
import io.ktor.utils.io.charsets.Charsets
import io.ktor.utils.io.core.buildPacket
import io.ktor.utils.io.core.writeText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.util.stringSafe
import me.rerere.search.SearchResult.SearchResultItem
import me.rerere.search.SearchService.Companion.httpClient
import me.rerere.search.SearchService.Companion.json
import org.jetbrains.compose.resources.stringResource
import rikkahub.search.generated.resources.Res
import rikkahub.search.generated.resources.searxng_desc_1
import rikkahub.search.generated.resources.searxng_desc_2

private const val TAG = "SearXNGService"

object SearXNGService : SearchService<SearchServiceOptions.SearXNGOptions> {
    override val name: String = "SearXNG"

    @Composable
    override fun Description() {
        Text(stringResource(Res.string.searxng_desc_1))
        Text(stringResource(Res.string.searxng_desc_2))
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
        serviceOptions: SearchServiceOptions.SearXNGOptions
    ): Result<SearchResult> = withContext(Dispatchers.IO) {
        runCatching {
            require(serviceOptions.url.isNotBlank()) {
                "SearXNG URL cannot be empty"
            }

            val query = params["query"]?.jsonPrimitive?.content ?: error("query is required")

            // 构建查询URL
            val baseUrl = serviceOptions.url.trimEnd('/')
            val encodedQuery = query.encodeURLParameter()
            val url = URLBuilder(Url("$baseUrl/search?q=$encodedQuery&format=json"))
                .apply {
                    if (serviceOptions.engines.isNotBlank()) {
                        parameters.append("engines", serviceOptions.engines)
                    }
                    if (serviceOptions.language.isNotBlank()) {
                        parameters.append("language", serviceOptions.language)
                    }
                }.build()

            // 发送请求
            val request = HttpRequestBuilder().apply {
                url(url)
                // 添加HTTP Basic Auth支持
                if (serviceOptions.username.isNotBlank() && serviceOptions.password.isNotBlank()) {
                    header(
                        "Authorization",
                        buildPacket {
                            writeText(
                                "${serviceOptions.username}:${serviceOptions.password}",
                                charset = Charsets.ISO_8859_1
                            )
                        }.encodeBase64()
                    )
                }
            }

            Logger.i(TAG) { "search: $url" }

            val response = httpClient.get(request)
            if (response.status.isSuccess()) {
                val bodyRaw = response.bodyAsText()
                val searchResponse = runCatching {
                    json.decodeFromString<SearXNGResponse>(bodyRaw)
                }.onFailure {
                    it.printStackTrace()
                    println("SearXNG response body: $bodyRaw")
                    error("Failed to decode SearXNG response: ${it.message}")
                }.getOrThrow()

                // 转换为标准格式，取前 N 个结果
                val items = searchResponse.results
                    .take(commonOptions.resultSize)
                    .map { result ->
                        SearchResultItem(
                            title = result.title,
                            url = result.url,
                            text = result.content
                        )
                    }

                return@withContext Result.success(SearchResult(items = items))
            } else {
                val errorBody = response.stringSafe()
                println("SearXNG API error: ${response.status.value} - $errorBody")
                error("SearXNG request failed with status ${response.status.value}")
            }
        }
    }

    override suspend fun scrape(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.SearXNGOptions
    ): Result<ScrapedResult> {
        return Result.failure(Exception("Scraping is not supported for SearXNG"))
    }


    @Serializable
    data class SearXNGResponse(
        @SerialName("query")
        val query: String,
        @SerialName("number_of_results")
        val numberOfResults: Int,
        @SerialName("results")
        val results: List<SearXNGResult>,
    )

    @Serializable
    data class SearXNGResult(
        @SerialName("url")
        val url: String,
        @SerialName("title")
        val title: String,
        @SerialName("content")
        val content: String,
        @SerialName("thumbnail")
        val thumbnail: String? = null,
        @SerialName("engine")
        val engine: String,
        @SerialName("template")
        val template: String,
        @SerialName("parsed_url")
        val parsedUrl: List<String> = emptyList(),
        @SerialName("img_src")
        val imgSrc: String? = null,
        @SerialName("priority")
        val priority: String? = null,
        @SerialName("engines")
        val engines: List<String> = emptyList(),
        @SerialName("positions")
        val positions: List<Int> = emptyList(),
        @SerialName("score")
        val score: Double = 0.0,
        @SerialName("category")
        val category: String = "",
        @SerialName("publishedDate")
        val publishedDate: String? = null,
        @SerialName("iframe_src")
        val iframeSrc: String? = null
    )
}
