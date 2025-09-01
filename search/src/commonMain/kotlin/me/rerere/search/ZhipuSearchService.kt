package me.rerere.search

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import io.ktor.client.request.*
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.util.stringSafe
import me.rerere.search.SearchResult.SearchResultItem
import me.rerere.search.SearchService.Companion.httpClient
import me.rerere.search.SearchService.Companion.json
import org.jetbrains.compose.resources.stringResource
import rikkahub.search.generated.resources.Res
import rikkahub.search.generated.resources.click_to_get_api_key

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
        serviceOptions: SearchServiceOptions.ZhipuOptions
    ): Result<SearchResult> = withContext(Dispatchers.IO) {
        runCatching {
            val query = params["query"]?.jsonPrimitive?.content ?: error("query is required")

            val body = buildJsonObject {
                put("search_query", JsonPrimitive(query))
                put("search_engine", JsonPrimitive("search_std"))
                put("count", JsonPrimitive(commonOptions.resultSize))
            }

            val request = HttpRequestBuilder().apply {
                url("https://open.bigmodel.cn/api/paas/v4/web_search")
                setBody(json.encodeToString(body))
                header("Authorization", "Bearer ${serviceOptions.apiKey}")
            }

            val response = httpClient.post(request)
            if (response.status.isSuccess()) {
                val bodyRaw = response.stringSafe() ?: error("Failed to get response body")
                val response = runCatching {
                    json.decodeFromString<ZhipuDto>(bodyRaw)
                }.onFailure {
                    it.printStackTrace()
                    println(bodyRaw)
                    error("Failed to decode response: $bodyRaw")
                }.getOrThrow()

                return@withContext Result.success(
                    SearchResult(
                        items = response.searchResult.map {
                            SearchResultItem(
                                title = it.title,
                                url = it.link,
                                text = it.content,
                            )
                        }
                    ))
            } else {
                println(response.stringSafe())
                error("response failed #${response.status.value}")
            }
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
