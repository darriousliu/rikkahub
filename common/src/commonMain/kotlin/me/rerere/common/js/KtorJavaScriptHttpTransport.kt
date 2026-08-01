package me.rerere.common.js

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
class KtorJavaScriptHttpTransport(
    private val httpClient: HttpClient,
) : JavaScriptHttpTransport {
    override fun newCall(request: JavaScriptHttpRequest): JavaScriptHttpCall =
        object : JavaScriptHttpCall {
            private val started = AtomicBoolean(false)
            private val callJob = SupervisorJob()

            override fun execute(): JavaScriptHttpResponse {
                check(started.compareAndSet(false, true)) {
                    "JavaScript HTTP calls can only be executed once"
                }
                return runBlocking(callJob) {
                    val outgoingBody = request.bodyForMethod()
                    val response = httpClient.request(request.url) {
                        method = HttpMethod.parse(request.method)
                        headers {
                            request.headers.forEach { (name, value) ->
                                if (outgoingBody == null || !name.equals(HttpHeaders.ContentType, ignoreCase = true)) {
                                    append(name, value)
                                }
                            }
                        }
                        outgoingBody?.let(::setBody)
                    }
                    JavaScriptHttpResponse(
                        status = response.status.value,
                        statusText = response.status.description,
                        body = response.body<String>(),
                    )
                }
            }

            override fun cancel() {
                callJob.cancel()
            }

            override fun close() = Unit
        }
}

private fun JavaScriptHttpRequest.bodyForMethod(): TextContent? {
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
