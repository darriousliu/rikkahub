package me.rerere.rikkahub.data.ai

import me.rerere.common.logging.LogEntry
import me.rerere.common.logging.Logging
import okhttp3.Interceptor
import okhttp3.Response
import okio.Buffer
import kotlin.time.TimeSource

internal interface RequestLogSink {
    val enabled: Boolean

    fun log(entry: LogEntry.RequestLog)
}

internal fun interface RequestTimeSource {
    fun markNow(): RequestTimeMark
}

internal fun interface RequestTimeMark {
    fun elapsedMilliseconds(): Long
}

private object LoggingRequestLogSink : RequestLogSink {
    override val enabled: Boolean
        get() = Logging.isRequestLoggingEnabled()

    override fun log(entry: LogEntry.RequestLog) {
        Logging.logRequest(entry)
    }
}

private object MonotonicRequestTimeSource : RequestTimeSource {
    override fun markNow(): RequestTimeMark {
        val startedAt = TimeSource.Monotonic.markNow()
        return RequestTimeMark { startedAt.elapsedNow().inWholeMilliseconds }
    }
}

class RequestLoggingInterceptor internal constructor(
    private val logSink: RequestLogSink,
    private val timeSource: RequestTimeSource,
) : Interceptor {
    constructor() : this(
        logSink = LoggingRequestLogSink,
        timeSource = MonotonicRequestTimeSource,
    )

    override fun intercept(chain: Interceptor.Chain): Response {
        if (!logSink.enabled) {
            return chain.proceed(chain.request())
        }

        val request = chain.request()
        val timeMark = timeSource.markNow()

        val requestHeaders = request.headers.toMap()
        val requestBody = request.body?.let { body ->
            val buffer = Buffer()
            body.writeTo(buffer)
            buffer.readUtf8()
        }

        val response: Response
        var error: String? = null

        try {
            response = chain.proceed(request)
        } catch (e: Exception) {
            error = e.message
            logSink.log(
                LogEntry.RequestLog(
                    tag = "HTTP",
                    url = request.url.toString(),
                    method = request.method,
                    requestHeaders = requestHeaders,
                    requestBody = requestBody,
                    error = error
                )
            )
            throw e
        }

        val durationMs = timeMark.elapsedMilliseconds()
        val responseHeaders = response.headers.toMap()

        logSink.log(
            LogEntry.RequestLog(
                tag = "HTTP",
                url = request.url.toString(),
                method = request.method,
                requestHeaders = requestHeaders,
                requestBody = requestBody,
                responseCode = response.code,
                responseHeaders = responseHeaders,
                durationMs = durationMs,
                error = error
            )
        )

        return response
    }

    private fun okhttp3.Headers.toMap(): Map<String, String> {
        return names().associateWith { get(it) ?: "" }
    }
}
