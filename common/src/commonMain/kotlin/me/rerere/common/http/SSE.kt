package me.rerere.common.http

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.accept
import io.ktor.client.request.prepareRequest
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.utils.io.readLine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

sealed class SseEvent {
    data object Open : SseEvent()

    data class Event(
        val id: String?,
        val type: String?,
        val data: String,
    ) : SseEvent()

    data object Closed : SseEvent()

    data class Failure(
        val throwable: Throwable?,
        val response: SseResponse?,
    ) : SseEvent()
}

data class SseResponse(
    val code: Int,
    val body: String? = null,
)

fun HttpClient.sseFlow(
    url: String,
    configure: HttpRequestBuilder.() -> Unit = {},
): Flow<SseEvent> = flow {
    try {
        prepareRequest(url) {
            accept(ContentType.Text.EventStream)
            configure()
        }.execute { response ->
            if (!response.status.isSuccess()) {
                emit(
                    SseEvent.Failure(
                        throwable = null,
                        response = SseResponse(
                            code = response.status.value,
                            body = response.bodyAsText(),
                        ),
                    )
                )
                return@execute
            }

            val contentType = response.headers[HttpHeaders.ContentType]?.let(ContentType::parse)
            if (contentType?.contentType != "text" || contentType.contentSubtype != "event-stream") {
                emit(
                    SseEvent.Failure(
                        throwable = IllegalStateException("Invalid content-type: $contentType"),
                        response = SseResponse(response.status.value),
                    )
                )
                return@execute
            }

            emit(SseEvent.Open)
            val channel = response.bodyAsChannel()
            var id: String? = null
            var type: String? = null
            val data = mutableListOf<String>()

            suspend fun dispatch() {
                if (data.isNotEmpty()) {
                    emit(SseEvent.Event(id = id, type = type, data = data.joinToString("\n")))
                }
                id = null
                type = null
                data.clear()
            }

            while (!channel.isClosedForRead) {
                val line = channel.readLine() ?: break
                if (line.isEmpty()) {
                    dispatch()
                    continue
                }
                if (line.startsWith(':')) continue

                val separator = line.indexOf(':')
                val field = if (separator >= 0) line.substring(0, separator) else line
                val rawValue = if (separator >= 0) line.substring(separator + 1) else ""
                val value = rawValue.removePrefix(" ")
                when (field) {
                    "id" -> if ('\u0000' !in value) id = value
                    "event" -> type = value
                    "data" -> data += value
                }
            }
            dispatch()
            emit(SseEvent.Closed)
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        emit(SseEvent.Failure(throwable = error, response = null))
    }
}
