package me.rerere.common.js

import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class OkHttpJavaScriptHttpTransport(
    private val httpClient: OkHttpClient,
) : JavaScriptHttpTransport {
    override fun newCall(request: JavaScriptHttpRequest): JavaScriptHttpCall {
        val requestBuilder = Request.Builder().url(request.url)
        request.headers.forEach { (name, value) -> requestBuilder.addHeader(name, value) }

        val mediaType = (request.headers["Content-Type"] ?: "application/json").toMediaType()
        when (request.method) {
            "GET" -> requestBuilder.get()
            "HEAD" -> requestBuilder.head()
            else -> {
                val body = request.body?.toRequestBody(mediaType)
                    ?: if (request.method in setOf("POST", "PUT", "PATCH")) {
                        "".toRequestBody(mediaType)
                    } else {
                        null
                    }
                requestBuilder.method(request.method, body)
            }
        }

        val call = httpClient.newCall(requestBuilder.build())
        return object : JavaScriptHttpCall {
            private val started = AtomicBoolean()
            private val completed = AtomicBoolean()
            private val response = AtomicReference<JavaScriptHttpResponse?>()
            private val failure = AtomicReference<Throwable?>()
            private val completion = CountDownLatch(1)

            override fun execute(): JavaScriptHttpResponse {
                check(started.compareAndSet(false, true)) { "JavaScript HTTP calls can only be executed once" }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        complete(Result.failure(e))
                    }

                    override fun onResponse(call: Call, response: Response) {
                        complete(
                            runCatching {
                                response.use {
                                    JavaScriptHttpResponse(
                                        status = it.code,
                                        statusText = it.message,
                                        body = it.body.string(),
                                    )
                                }
                            },
                        )
                    }
                })
                try {
                    completion.await()
                } catch (error: InterruptedException) {
                    call.cancel()
                    throw error
                }
                failure.get()?.let { throw it }
                return checkNotNull(response.get())
            }

            private fun complete(result: Result<JavaScriptHttpResponse>) {
                if (completed.compareAndSet(false, true)) {
                    result.fold(response::set, failure::set)
                    completion.countDown()
                }
            }

            override fun cancel() {
                call.cancel()
            }

            override fun close() = Unit
        }
    }
}

internal fun javaScriptHttpTransportForTest(): JavaScriptHttpTransport =
    OkHttpJavaScriptHttpTransport(OkHttpClient())
