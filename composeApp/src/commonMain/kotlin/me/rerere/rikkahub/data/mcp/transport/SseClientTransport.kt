package me.rerere.rikkahub.data.mcp.transport

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.sse
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.URLBuilder
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.path
import io.ktor.http.takeFrom
import io.modelcontextprotocol.kotlin.sdk.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.shared.AbstractTransport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import me.rerere.ai.util.stringSafe
import me.rerere.rikkahub.data.mcp.McpJson
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

private const val TAG = "SseClientTransport"

@OptIn(ExperimentalAtomicApi::class)
internal class SseClientTransport(
    private val client: HttpClient,
    private val urlString: String,
    private val headers: List<Pair<String, String>>,
) : AbstractTransport() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initialized: AtomicBoolean = AtomicBoolean(false)
    private val endpoint = CompletableDeferred<String>()

    private var job: Job? = null

    private val baseUrl by lazy {
        URLBuilder()
            .takeFrom(urlString)
            .apply {
                path() // set path to empty
                parameters.clear() //  clear parameters
            }
            .build()
            .toString()
    }

    override suspend fun start() {
        if (!initialized.compareAndSet(false, true)) {
            error(
                "SSEClientTransport already started! " +
                    "If using Client class, note that connect() calls start() automatically.",
            )
        }

        Logger.i(TAG) { "start: $urlString" }

        client.sse(
            request = {
                url(urlString)
                headers {
                    for ((key, value) in this@SseClientTransport.headers) {
                        append(key, value)
                    }
                }
            }
        ) {
            try {
                Logger.i(TAG) { "onOpen: $urlString" }
                incoming.collect { event ->
                    val id = event.id
                    val type = event.event
                    val data = event.data ?: return@collect
                    Logger.i(TAG) { "onEvent:  #$id($type) - $data" }
                    when (type) {
                        "error" -> {
                            val e = IllegalStateException("SSE error: $data")
                            _onError(e)
                            throw e
                        }

                        "open" -> {
                            // The connection is open, but we need to wait for the endpoint to be received.
                        }

                        "endpoint" -> {
                            val endpointData =
                                if (data.startsWith("http://") || data.startsWith("https://")) {
                                    // 绝对路径，直接使用
                                    data
                                } else {
                                    // 相对路径，加上baseUrl
                                    baseUrl + if (data.startsWith("/")) data else "/$data"
                                }
                            endpoint.complete(endpointData)
                        }

                        else -> {
                            scope.launch {
                                try {
                                    val message = McpJson.decodeFromString<JSONRPCMessage>(data)
                                    _onMessage(message)
                                } catch (e: Exception) {
                                    _onError(e)
                                }
                            }
                        }
                    }
                }
            } catch (t: Exception) {
                t.printStackTrace()
                Logger.i(TAG) { "onFailure: $urlString / $t" }
                endpoint.completeExceptionally(t)
                _onError(t)
                _onClose()
            } finally {
                Logger.i(TAG) { "onClosed: $urlString" }
            }
        }
        endpoint.await()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun send(message: JSONRPCMessage) {
        if (!endpoint.isCompleted) {
            error("Not connected")
        }


        try {
            val request = HttpRequestBuilder().apply {
                url(endpoint.getCompleted())
                this@SseClientTransport.headers.forEach { (key, value) ->
                    header(key, value)
                }
                contentType(ContentType.Application.Json)
                setBody(McpJson.encodeToString(message))
            }
            val response = client.post(request)

            if (!response.status.isSuccess()) {
                val text = response.stringSafe()
                error("Error POSTing to endpoint ${endpoint.getCompleted()} (HTTP ${response.status.value}): $text")
            }
        } catch (e: Exception) {
            _onError(e)
            throw e
        }
    }

    override suspend fun close() {
        if (!initialized.load()) {
            error("SSEClientTransport is not initialized!")
        }

        _onClose()
        job?.cancelAndJoin()
    }
}
