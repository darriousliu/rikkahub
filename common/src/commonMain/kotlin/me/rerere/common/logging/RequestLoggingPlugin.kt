package me.rerere.common.logging

import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import kotlin.time.TimeSource

private const val HTTP_LOG_TAG = "HTTP"

/**
 * Records outgoing requests into [Logging] so the in-app log page can show them.
 *
 * Response bodies are never read: provider responses are streamed and consuming them here would
 * break SSE. This matches what the Android OkHttp interceptor captures.
 */
val RequestLoggingPlugin = createClientPlugin("RequestLogging") {
    on(Send) { request ->
        if (!Logging.isRequestLoggingEnabled()) return@on proceed(request)

        val url = request.url.buildString()
        val method = request.method.value
        val requestHeaders = request.headersSnapshot()
        val requestBody = (request.body as? OutgoingContent)?.loggableText()
        val startedAt = TimeSource.Monotonic.markNow()

        val call = try {
            proceed(request)
        } catch (error: Throwable) {
            Logging.logRequest(
                LogEntry.RequestLog(
                    tag = HTTP_LOG_TAG,
                    url = url,
                    method = method,
                    requestHeaders = requestHeaders,
                    requestBody = requestBody,
                    error = error.message,
                ),
            )
            throw error
        }

        Logging.logRequest(
            LogEntry.RequestLog(
                tag = HTTP_LOG_TAG,
                url = url,
                method = method,
                requestHeaders = requestHeaders,
                requestBody = requestBody,
                responseCode = call.response.status.value,
                responseHeaders = call.response.headers.entries()
                    .associate { header -> header.key to header.value.joinToString(", ") },
                durationMs = startedAt.elapsedNow().inWholeMilliseconds,
            ),
        )
        call
    }
}

private fun HttpRequestBuilder.headersSnapshot(): Map<String, String> =
    headers.entries().associate { header -> header.key to header.value.joinToString(", ") }

/** Only in-memory payloads are readable here; streamed bodies stay unlogged. */
private fun OutgoingContent.loggableText(): String? = when (this) {
    is TextContent -> text
    is ByteArrayContent -> bytes().decodeToString()
    else -> null
}
