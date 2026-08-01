package me.rerere.tts.provider.providers

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel

internal class RemoteTtsHttpResponse(
    private val response: HttpResponse,
) {
    val code: Int
        get() = response.status.value

    val message: String
        get() = response.status.description

    val isSuccessful: Boolean
        get() = response.status.isSuccess()

    suspend fun bodyBytes(): ByteArray = response.body()

    suspend fun bodyText(): String = response.bodyAsText()

    suspend fun bodyChannel(): ByteReadChannel = response.bodyAsChannel()
}

internal suspend fun HttpClient.postRemoteTtsRequest(
    url: String,
    body: String,
    headers: Map<String, String>,
    timeoutMillis: Long = 120_000,
): RemoteTtsHttpResponse {
    val response = request(url) {
        method = HttpMethod.Post
        headers.forEach { (name, value) ->
            this.headers.append(name, value)
        }
        setBody(ByteArrayContent(body.encodeToByteArray(), contentType = null))
        timeout {
            requestTimeoutMillis = timeoutMillis
            socketTimeoutMillis = timeoutMillis
        }
    }
    return RemoteTtsHttpResponse(response)
}
