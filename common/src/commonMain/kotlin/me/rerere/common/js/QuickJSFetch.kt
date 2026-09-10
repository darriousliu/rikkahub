package me.rerere.common.js

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.function
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal suspend fun QuickJs.installFetch(
    httpClient: HttpClient,
    executionState: JavaScriptExecutionState,
    fetchDispatcher: CoroutineDispatcher,
) {
    function<String>("__httpRequest") { args ->
        val url = args.getOrNull(0) as? String ?: error("url is required")
        val method = (args.getOrNull(1) as? String ?: "GET").uppercase()
        val headersJson = args.getOrNull(2) as? String
        val body = args.getOrNull(3) as? String
        val headers = if (!headersJson.isNullOrBlank() && headersJson != "null") {
            javaScriptJson.parseToJsonElement(headersJson).jsonObject
                .mapValues { (_, value) -> value.jsonPrimitive.content }
        } else {
            emptyMap()
        }

        val callJob = SupervisorJob()
        try {
            executionState.install(callJob)
            val response = runBlocking(fetchDispatcher) {
                runBlocking(callJob) {
                    val outgoingBody = bodyForMethod(method, body, headers)
                    val response = httpClient.request(url) {
                        this.method = HttpMethod.parse(method)
                        headers {
                            headers.forEach { (name, value) ->
                                if (outgoingBody == null || !name.equals(HttpHeaders.ContentType, ignoreCase = true)) {
                                    append(name, value)
                                }
                            }
                        }
                        outgoingBody?.let(::setBody)
                    }
                    HttpResponseDto(
                        status = response.status.value,
                        ok = response.status.value in 200..299,
                        statusText = response.status.description,
                        body = response.body<String>(),
                    )
                }
            }
            javaScriptJson.encodeToString(response)
        } finally {
            executionState.clear(callJob)
        }
    }

    evaluate<Any?>(FETCH_POLYFILL + "\n;void 0;", "fetch-polyfill.js")
}

private fun bodyForMethod(method: String, body: String?, headers: Map<String, String>): TextContent? {
    if (method == HttpMethod.Get.value || method == HttpMethod.Head.value) return null
    val content = body ?: if (method in METHODS_REQUIRING_BODY) "" else return null
    val contentType = headers.entries
        .firstOrNull { (name) -> name.equals(HttpHeaders.ContentType, ignoreCase = true) }
        ?.value
        ?.let(ContentType::parse)
        ?: ContentType.Application.Json
    val encodedContentType = if (contentType.parameters.none { it.name.equals("charset", ignoreCase = true) }) {
        contentType.withParameter("charset", "utf-8")
    } else {
        contentType
    }
    return TextContent(content, encodedContentType)
}

private val METHODS_REQUIRING_BODY = setOf("POST", "PUT", "PATCH")

@Serializable
private data class HttpResponseDto(
    val status: Int,
    val ok: Boolean,
    val statusText: String,
    val body: String,
)

// fetch() intentionally stays synchronous to preserve existing user scripts.
private const val FETCH_POLYFILL = """
globalThis.fetch = function(url, options) {
    options = options || {};
    var method = (options.method || 'GET').toUpperCase();
    var headers = options.headers ? JSON.stringify(options.headers) : null;
    var body = options.body;
    if (typeof body === 'object' && body !== null) {
        body = JSON.stringify(body);
    } else if (typeof body !== 'string') {
        body = null;
    }

    var raw = __httpRequest(url, method, headers, body);
    var data = JSON.parse(raw);
    return {
        status: data.status,
        ok: data.ok,
        statusText: data.statusText,
        url: url,
        _body: data.body,
        text: function() { return this._body; },
        json: function() { return JSON.parse(this._body); }
    };
};
"""
