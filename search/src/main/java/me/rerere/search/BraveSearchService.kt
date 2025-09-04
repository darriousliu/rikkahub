package me.rerere.search

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.search.SearchResult.SearchResultItem
import me.rerere.search.SearchService.Companion.httpClient
import me.rerere.search.SearchService.Companion.json

private const val TAG = "BraveSearchService"

object BraveSearchService : SearchService<SearchServiceOptions.BraveOptions> {
    override val name: String = "Brave"

    @Composable
    override fun Description() {
        val urlHandler = LocalUriHandler.current
        TextButton(
            onClick = {
                urlHandler.openUri("https://api.search.brave.com/")
            }
        ) {
            Text(stringResource(R.string.click_to_get_api_key))
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

    override suspend fun search(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.BraveOptions
    ): Result<SearchResult> = withContext(Dispatchers.IO) {
        runCatching {
            val query = params["query"]?.jsonPrimitive?.content ?: error("query is required")
            val url = "https://api.search.brave.com/res/v1/web/search" +
                "?q=${java.net.URLEncoder.encode(query, "UTF-8")}" +
                "&count=${commonOptions.resultSize}"

            val request = HttpRequestBuilder().apply {
                url(url)
                header("Accept", "application/json")
                header("X-Subscription-Token", serviceOptions.apiKey)
            }

            val response = httpClient.get(request)
            if (response.status.isSuccess()) {
                val responseBody = response.bodyAsText()
                val searchResponse = json.decodeFromString<BraveSearchResponse>(responseBody)

                val items = searchResponse.web?.results?.map { result ->
                    SearchResultItem(
                        title = result.title,
                        url = result.url,
                        text = result.description ?: ""
                    )
                } ?: emptyList()

                return@withContext Result.success(
                    SearchResult(
                        answer = null,
                        items = items
                    )
                )
            } else {
                error("Brave search failed with code ${response.status.value}: ${response.status.description}")
            }
        }
    }

    @Serializable
    data class BraveSearchResponse(
        val type: String? = null,
        val web: WebResults? = null,
    )

    @Serializable
    data class WebResults(
        val type: String? = null,
        val results: List<WebResult>? = null,
    )

    @Serializable
    data class WebResult(
        val type: String,
        val title: String,
        val url: String,
        val description: String? = null,
    )
}
