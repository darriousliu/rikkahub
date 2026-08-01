package me.rerere.search

import me.rerere.common.logging.RikkaLog as Log
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import me.rerere.search.generated.resources.Res
import me.rerere.search.generated.resources.searxng_desc_1
import me.rerere.search.generated.resources.searxng_desc_2
import org.jetbrains.compose.resources.stringResource
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.search.SearchResult.SearchResultItem
import me.rerere.search.SearchService.Companion.httpClient
import me.rerere.search.SearchService.Companion.json
import kotlin.io.encoding.Base64

private const val TAG = "SearXNGService"

object SearXNGService : SearchService<SearchServiceOptions.SearXNGOptions> {
    override val name: String = "SearXNG"

    @Composable
    override fun Description() {
        Text(stringResource(Res.string.searxng_desc_1))
        Text(stringResource(Res.string.searxng_desc_2))
    }

    override fun parameters(options: SearchServiceOptions.SearXNGOptions): InputSchema? =
        InputSchema.Obj(
            properties = buildJsonObject {
                put("query", buildJsonObject {
                    put("type", "string")
                    put("description", "search keyword")
                })
            },
            required = listOf("query")
        )

    override fun scrapingParameters(options: SearchServiceOptions.SearXNGOptions): InputSchema? = null

    override suspend fun search(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.SearXNGOptions
    ): Result<SearchResult> = runCatching {
            require(serviceOptions.url.isNotBlank()) {
                "SearXNG URL cannot be empty"
            }

            val query = params["query"]?.jsonPrimitive?.content ?: error("query is required")

            // 构建查询URL
            val baseUrl = serviceOptions.url.trimEnd('/')
            val encodedQuery = encodeSearchQuery(query)
            val url = buildString {
                append("$baseUrl/search?q=$encodedQuery&format=json")
                if (serviceOptions.engines.isNotBlank()) {
                    append("&engines=${encodeSearchQuery(serviceOptions.engines)}")
                }
                if (serviceOptions.language.isNotBlank()) {
                    append("&language=${encodeSearchQuery(serviceOptions.language)}")
                }
            }
            val headers = buildMap {
                if (serviceOptions.username.isNotBlank() && serviceOptions.password.isNotBlank()) {
                    val credentials = "${serviceOptions.username}:${serviceOptions.password}"
                    put("Authorization", "Basic ${Base64.Default.encode(credentials.encodeIso88591())}")
                }
            }

            Log.i(TAG, "search: $url")

            val response = httpClient.executeSearchRequest(url = url, headers = headers)
            if (response.isSuccessful) {
                val bodyRaw = response.body
                val searchResponse = runCatching {
                    json.decodeFromString<SearXNGResponse>(bodyRaw)
                }.onFailure {
                    println(it.stackTraceToString())
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

                SearchResult(items = items)
            } else {
                val errorBody = response.body
                println("SearXNG API error: ${response.code} - $errorBody")
                error("SearXNG request failed with status ${response.code}")
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
    )
}

internal fun String.encodeIso88591(): ByteArray = ByteArray(length) { index ->
    this[index].code.takeIf { code -> code <= 0xff }?.toByte() ?: '?'.code.toByte()
}
