package me.rerere.search

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import co.touchlab.kermit.Logger
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
import rikkahub.search.generated.resources.click_to_get_api_key

private const val PERPLEXITY_ENDPOINT = "https://api.perplexity.ai/search"
private const val TAG = "PerplexitySearchService"

object PerplexitySearchService : SearchService<SearchServiceOptions.PerplexityOptions> {
    override val name: String = "Perplexity"

    @Composable
    override fun Description() {
        val uriHandler = LocalUriHandler.current
        TextButton(
            onClick = {
                uriHandler.openUri("https://www.perplexity.ai/settings/api")
            }
        ) {
            Text(stringResource(Res.string.click_to_get_api_key))
        }
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
        serviceOptions: SearchServiceOptions.PerplexityOptions
    ): Result<SearchResult> = withContext(Dispatchers.IO) {
        runCatching {
            if (serviceOptions.apiKey.isBlank()) {
                error("Perplexity API key is required")
            }

            val query = params["query"]?.jsonPrimitive?.content
                ?: error("query is required")

            val body = buildJsonObject {
                put("query", JsonPrimitive(query))
                put("max_results", JsonPrimitive(commonOptions.resultSize))
                serviceOptions.maxTokensPerPage?.let {
                    if (it > 0) {
                        put("max_tokens_per_page", JsonPrimitive(it))
                    }
                }
            }

            Logger.i(TAG) { "search: $body" }

            val request = HttpRequestBuilder().apply {
                url(PERPLEXITY_ENDPOINT)
                setBody(body.toString())
                header("Authorization", "Bearer ${serviceOptions.apiKey}")
                header("Content-Type", "application/json")
            }

            val response = httpClient.post(request)
            if (response.status.isSuccess()) {
                val responseBody = response.bodyAsText().let {
                    json.decodeFromString<PerplexityResponse>(it)
                }

                val items = responseBody.results
                    .filter { !it.title.isNullOrBlank() && !it.url.isNullOrBlank() }
                    .take(commonOptions.resultSize)
                    .map {
                        SearchResultItem(
                            title = it.title!!,
                            url = it.url!!,
                            text = it.snippet ?: it.text ?: ""
                        )
                    }

                return@withContext Result.success(
                    SearchResult(
                        answer = responseBody.answer,
                        items = items
                    )
                )
            } else {
                error("response failed #${response.status.value}: ${response.stringSafe()}")
            }
        }
    }

    override suspend fun scrape(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.PerplexityOptions
    ): Result<ScrapedResult> {
        return Result.failure(Exception("Scraping is not supported for Perplexity"))
    }

    @Serializable
    private data class PerplexityResponse(
        val answer: String? = null,
        val results: List<ResultItem> = emptyList()
    ) {
        @Serializable
        data class ResultItem(
            val title: String? = null,
            val url: String? = null,
            val snippet: String? = null,
            @SerialName("text") val text: String? = null,
        )
    }
}
