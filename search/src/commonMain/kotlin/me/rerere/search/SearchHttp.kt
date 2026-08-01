package me.rerere.search

import io.ktor.client.HttpClient
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.content.ByteArrayContent
import kotlinx.coroutines.withTimeout

internal data class SearchHttpResponse(
    val code: Int,
    val message: String,
    val body: String,
) {
    val isSuccessful: Boolean
        get() = code in 200..299
}

internal suspend fun HttpClient.executeSearchRequest(
    url: String,
    method: HttpMethod = HttpMethod.Get,
    body: String? = null,
    headers: Map<String, String> = emptyMap(),
    timeoutMillis: Long? = null,
): SearchHttpResponse {
    suspend fun execute(): SearchHttpResponse {
        val response = request(url) {
            this.method = method
            headers.forEach { (name, value) ->
                this.headers.append(name, value)
            }
            body?.let {
                setBody(ByteArrayContent(it.encodeToByteArray(), contentType = null))
            }
        }
        return SearchHttpResponse(
            code = response.status.value,
            message = response.status.description,
            body = response.bodyAsText(),
        )
    }

    return timeoutMillis?.let { withTimeout(it) { execute() } } ?: execute()
}

internal suspend fun HttpClient.postSearchRequest(
    url: String,
    body: String,
    headers: Map<String, String> = emptyMap(),
): SearchHttpResponse = executeSearchRequest(
    url = url,
    method = HttpMethod.Post,
    body = body,
    headers = headers,
)
