package me.rerere.search

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

object ZhipuSearchService : SearchService<SearchServiceOptions.ZhipuOptions> {
    override val name: String = "Zhipu"

    @Composable
    override fun Description() {
        val urlHandler = LocalUriHandler.current
        TextButton(
            onClick = {
                urlHandler.openUri("https://bigmodel.cn/usercenter/proj-mgmt/apikeys")
            }
        ) {
            Text(stringResource(Res.string.click_to_get_api_key))
        }
    }

    override fun parameters(options: SearchServiceOptions.ZhipuOptions): InputSchema? =
        InputSchema.Obj(
            properties = buildJsonObject {
                put("query", buildJsonObject {
                    put("type", "string")
                    put("description", "search keyword")
                })
            },
            required = listOf("query")
        )

    override fun scrapingParameters(options: SearchServiceOptions.ZhipuOptions): InputSchema? = null

    override suspend fun search(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.ZhipuOptions
    ): Result<SearchResult> = runCatching {
        val query = params["query"]?.jsonPrimitive?.content ?: error("query is required")

        val body = buildJsonObject {
            put("search_query", JsonPrimitive(query))
            put("search_engine", JsonPrimitive("search_std"))
            put("count", JsonPrimitive(commonOptions.resultSize))
        }

        val response = httpClient.postSearchRequest(
            url = "https://open.bigmodel.cn/api/paas/v4/web_search",
            body = json.encodeToString(body),
            headers = mapOf(
                "Authorization" to "Bearer ${serviceOptions.apiKey}",
                "Content-Type" to "application/json",
            ),
        )
        if (response.isSuccessful) {
            val bodyRaw = response.body
            val response = runCatching {
                json.decodeFromString<ZhipuDto>(bodyRaw)
            }.onFailure {
                println(it.stackTraceToString())
                println(bodyRaw)
                error("Failed to decode response: $bodyRaw")
            }.getOrThrow()

            SearchResult(
                items = response.searchResult.map {
                    SearchResultItem(
                        title = it.title,
                        url = it.link,
                        text = it.content,
                    )
                },
            )
        } else {
            println(response.body)
            error("response failed #${response.code}")
        }
    }

    override suspend fun scrape(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.ZhipuOptions
    ): Result<ScrapedResult> {
        return Result.failure(Exception("Scraping is not supported for Zhipu"))
    }

    @Serializable
    data class ZhipuDto(
        @SerialName("search_result")
        val searchResult: List<ZhipuSearchResultDto>
    )

    @Serializable
    data class ZhipuSearchResultDto(
        @SerialName("content")
        val content: String,
        @SerialName("icon")
        val icon: String?,
        @SerialName("link")
        val link: String,
        @SerialName("media")
        val media: String?,
        @SerialName("refer")
        val refer: String?,
        @SerialName("title")
        val title: String
    )
}
