package me.rerere.search

import me.rerere.common.logging.RikkaLog as Log
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import me.rerere.search.generated.resources.Res
import me.rerere.search.generated.resources.click_to_get_api_key
import org.jetbrains.compose.resources.stringResource
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.search.SearchResult.SearchResultItem
import me.rerere.search.SearchService.Companion.httpClient
import me.rerere.search.SearchService.Companion.json

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

    override fun parameters(options: SearchServiceOptions.PerplexityOptions): InputSchema? =
        InputSchema.Obj(
            properties = buildJsonObject {
                put("query", buildJsonObject {
                    put("type", "string")
                    put("description", "search keyword")
                })
            },
            required = listOf("query")
        )

    override fun scrapingParameters(options: SearchServiceOptions.PerplexityOptions): InputSchema? = null

    override suspend fun search(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.PerplexityOptions
    ): Result<SearchResult> = runCatching {
            if (serviceOptions.apiKey.isBlank()) {
                error("Perplexity API key is required")
            }

            val query = params["query"]?.jsonPrimitive?.content
                ?: error("query is required")

            val body = buildJsonObject {
                put("query", JsonPrimitive(query))
                put("max_results", JsonPrimitive(commonOptions.resultSize))
                serviceOptions.maxTokens?.let {
                    if (it > 0) {
                        put("max_tokens", JsonPrimitive(it))
                    }
                }
                serviceOptions.maxTokensPerPage?.let {
                    if (it > 0) {
                        put("max_tokens_per_page", JsonPrimitive(it))
                    }
                }
            }

            Log.i(TAG, "search: $body")

            val response = httpClient.postSearchRequest(
                url = PERPLEXITY_ENDPOINT,
                body = body.toString(),
                headers = mapOf(
                    "Authorization" to "Bearer ${serviceOptions.apiKey}",
                    "Content-Type" to "application/json",
                ),
            )
            if (response.isSuccessful) {
                val responseBody = response.body.let {
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

                SearchResult(
                    answer = responseBody.answer,
                    items = items
                )
            } else {
                error("response failed #${response.code}: ${response.body}")
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
