package me.rerere.search

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.common.js.DefaultJavaScriptExecutor
import me.rerere.common.js.JavaScriptExecutionRequest
import me.rerere.common.js.JavaScriptExecutor
import me.rerere.common.js.OkHttpJavaScriptHttpTransport
import me.rerere.common.js.textOrNull
import me.rerere.search.SearchService.Companion.httpClient
import me.rerere.search.SearchService.Companion.json
import me.rerere.search.generated.resources.Res
import me.rerere.search.generated.resources.custom_js_desc
import org.jetbrains.compose.resources.stringResource

object CustomJsSearchService : SearchService<SearchServiceOptions.CustomJsOptions> {
    override val name: String = "Custom JS"

    @Composable
    override fun Description() {
        Text(stringResource(Res.string.custom_js_desc))
    }

    override fun parameters(options: SearchServiceOptions.CustomJsOptions): InputSchema? =
        InputSchema.Obj(
            properties = buildJsonObject {
                put("query", buildJsonObject {
                    put("type", "string")
                    put("description", "search keyword")
                })
            },
            required = listOf("query")
        )

    override fun scrapingParameters(options: SearchServiceOptions.CustomJsOptions): InputSchema? {
        if (options.scrapeScript.isBlank()) return null
        return InputSchema.Obj(
            properties = buildJsonObject {
                put("urls", buildJsonObject {
                    put("type", "array")
                    put("description", "urls to scrape")
                    put("items", buildJsonObject {
                        put("type", "string")
                    })
                })
            },
            required = listOf("urls")
        )
    }

    override suspend fun search(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.CustomJsOptions,
    ): Result<SearchResult> = resultCatching {
        val query = params["query"]?.jsonPrimitive?.content ?: error("query is required")
        val script = serviceOptions.searchScript.ifBlank { error("Search script is empty") }

        val resultJson = executeCustomJavaScript(
            executor = DefaultJavaScriptExecutor(OkHttpJavaScriptHttpTransport(httpClient)),
            userScript = script,
            invocation = "search(${quoteJsString(query)}, ${commonOptions.resultSize})",
        )

        withContext(Dispatchers.Default) {
            json.decodeFromString<SearchResult>(resultJson)
        }
    }

    override suspend fun scrape(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.CustomJsOptions,
    ): Result<ScrapedResult> = resultCatching {
        val script = serviceOptions.scrapeScript.ifBlank { error("Scrape script is empty") }
        val urlsJson = params["urls"]?.toString() ?: error("urls is required")

        val resultJson = executeCustomJavaScript(
            executor = DefaultJavaScriptExecutor(OkHttpJavaScriptHttpTransport(httpClient)),
            userScript = script,
            invocation = "scrape($urlsJson)",
        )

        withContext(Dispatchers.Default) {
            json.decodeFromString<ScrapedResult>(resultJson)
        }
    }

    internal fun quoteJsString(s: String): String {
        val sb = StringBuilder("\"")
        for (ch in s) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> sb.append(ch)
            }
        }
        sb.append("\"")
        return sb.toString()
    }
}

internal suspend fun executeCustomJavaScript(
    executor: JavaScriptExecutor,
    userScript: String,
    invocation: String,
): String {
    return executor.execute(
        JavaScriptExecutionRequest(
            setupScripts = listOf(userScript),
            code = "JSON.stringify($invocation)",
        ),
    ).value.textOrNull() ?: error("Function returned null or undefined")
}

private suspend fun <T> resultCatching(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (error: Throwable) {
    Result.failure(error)
}
