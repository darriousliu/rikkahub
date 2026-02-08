package me.rerere.rikkahub.data.ai

import io.ktor.client.call.HttpClientCall
import io.ktor.client.plugins.HttpSend
import io.ktor.client.statement.HttpResponse
import io.ktor.util.toMap
import me.rerere.common.android.LogEntry
import me.rerere.common.android.Logging
import kotlin.time.Clock

fun HttpSend.requestLoggingInterceptor() = intercept { request ->
    val startTime = Clock.System.now().toEpochMilliseconds()

    val requestHeaders = request.headers.build().toMap()
    val requestBody = request.body.toString()

    val call: HttpClientCall
    val response: HttpResponse
    var error: String? = null

    try {
        call = execute(request)
        response = call.response
    } catch (e: Exception) {
        error = e.message
        Logging.logRequest(
            LogEntry.RequestLog(
                tag = "HTTP",
                url = request.url.toString(),
                method = request.method.value,
                requestHeaders = requestHeaders.mapValues { it.value.joinToString() },
                requestBody = requestBody,
                error = error
            )
        )
        throw e
    }

    val durationMs = Clock.System.now().toEpochMilliseconds() - startTime
    val responseHeaders = response.headers.toMap()

    Logging.logRequest(
        LogEntry.RequestLog(
            tag = "HTTP",
            url = request.url.toString(),
            method = request.method.value,
            requestHeaders = requestHeaders.mapValues { it.value.joinToString() },
            requestBody = requestBody,
            responseCode = response.status.value,
            responseHeaders = responseHeaders.mapValues { it.value.joinToString() },
            durationMs = durationMs,
            error = error
        )
    )
    call
}
