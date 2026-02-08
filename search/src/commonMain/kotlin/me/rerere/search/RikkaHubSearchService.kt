package me.rerere.search

import androidx.compose.runtime.Composable
import co.touchlab.kermit.Logger
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
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

private const val TAG = "RikkaHubSearchService"

object RikkaHubSearchService : SearchService<SearchServiceOptions.RikkaHubOptions> {
    override val name: String = "RikkaHub"

    @Composable
    override fun Description() {
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

    override val scrapingParameters: InputSchema?
        get() = null

    override suspend fun search(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.RikkaHubOptions
    ): Result<SearchResult> = withContext(Dispatchers.IO) {
        runCatching {
            val query = params["query"]?.jsonPrimitive?.content ?: error("query is required")
            val body = buildJsonObject {
                put("q", JsonPrimitive(query))
                put("depth", JsonPrimitive(serviceOptions.depth))
                put("outputType", JsonPrimitive("sourcedAnswer"))
                put("includeImages", JsonPrimitive("false"))
            }

            val request = HttpRequestBuilder().apply {
                url("https://api.rikka-ai.com/v1/search")
                header("Authorization", "Bearer ${serviceOptions.apiKey}")
                header("Content-Type", "application/json")
                method = HttpMethod.Post
                contentType(ContentType.Application.Json)
                setBody(body.toString())
            }

            Logger.i(TAG) { "search: $query" }

            val response = httpClient.request(request)
            if (response.status.isSuccess()) {
                val responseBody = response.bodyAsText().let {
                    json.decodeFromString<RikkaHubSearchResponse>(it)
                }

                return@withContext Result.success(
                    SearchResult(
                        answer = responseBody.answer,
                        items = responseBody.sources.take(commonOptions.resultSize).map {
                            SearchResultItem(
                                title = it.name,
                                url = it.url,
                                text = it.snippet
                            )
                        }
                    )
                )
            } else {
                error("response failed #${response.status.value}: ${response.bodyAsText()}")
            }
        }
    }

    override suspend fun scrape(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.RikkaHubOptions
    ): Result<ScrapedResult> {
        error("RikkaHub does not support scraping")
    }

    @Serializable
    data class RikkaHubSearchResponse(
        val answer: String,
        val sources: List<Source>
    )

    @Serializable
    data class Source(
        val name: String,
        val url: String,
        val snippet: String
    )
}
