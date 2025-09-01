package me.rerere.rikkahub.data.ai.mcp.transport

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.sse
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpMethod
import io.ktor.http.isSuccess
import io.ktor.utils.io.exhausted
import io.ktor.utils.io.readUTF8Line
import io.modelcontextprotocol.kotlin.sdk.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.JSONRPCNotification
import io.modelcontextprotocol.kotlin.sdk.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.JSONRPCResponse
import io.modelcontextprotocol.kotlin.sdk.RequestId
import io.modelcontextprotocol.kotlin.sdk.shared.AbstractTransport
import io.modelcontextprotocol.kotlin.sdk.shared.McpJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import me.rerere.ai.util.stringSafe
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

private const val TAG = "StreamableHttpClientTra"

private const val MCP_SESSION_ID_HEADER = "mcp-session-id"
private const val MCP_PROTOCOL_VERSION_HEADER = "mcp-protocol-version"
private const val MCP_RESUMPTION_TOKEN_HEADER = "Last-Event-ID"

class StreamableHttpError(
    val code: Int? = null,
    message: String? = null
) : Exception("Streamable HTTP error: $message")

@OptIn(ExperimentalAtomicApi::class)
class StreamableHttpClientTransport(
    private val client: HttpClient,
    private val url: String,
    private val headers: Map<String, String> = emptyMap(),
) : AbstractTransport() {
    var sessionId: String? = null
        private set
    var protocolVersion: String? = null

    private val initialized: AtomicBoolean = AtomicBoolean(false)

    private var sseJob: Job? = null

    private val scope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    private var lastEventId: String? = null

    override suspend fun start() {
        if (!initialized.compareAndSet(expectedValue = false, newValue = true)) {
            error("StreamableHttpClientTransport already started!")
        }
        Logger.d(TAG) { "start: Client transport starting..." }
    }

    /**
     * Sends a single message with optional resumption support
     */
    override suspend fun send(message: JSONRPCMessage) {
        send(message, null)
    }

    /**
     * Sends one or more messages with optional resumption support.
     * This is the main send method that matches the TypeScript implementation.
     */
    suspend fun send(
        message: JSONRPCMessage,
        resumptionToken: String?,
        onResumptionToken: ((String) -> Unit)? = null
    ) {
        Logger.d(TAG) { "send: Client sending message via POST to $url: ${McpJson.encodeToString(message)}" }

        // If we have a resumption token, reconnect the SSE stream with it
        resumptionToken?.let { token ->
            startSseSession(
                resumptionToken = token,
                onResumptionToken = onResumptionToken,
                replayMessageId = if (message is JSONRPCRequest) message.id else null
            )
            return
        }

        val jsonBody = McpJson.encodeToString(message)

        val request = HttpRequestBuilder().apply {
            url(this@StreamableHttpClientTransport.url)
            setBody(jsonBody)
            applyCommonHeaders()
            header("Accept", "application/json, text/event-stream")
        }

        val response = client.post(request)


        response.let { resp ->
            resp.headers[MCP_SESSION_ID_HEADER].let {
                sessionId = it
            }

            if (resp.status.value == 202) { // HTTP_ACCEPTED
                if (message is JSONRPCNotification && message.method == "notifications/initialized") {
                    startSseSession(onResumptionToken = onResumptionToken)
                }
                return
            }

            if (!resp.status.isSuccess()) {
                val error = StreamableHttpError(resp.status.value, resp.stringSafe())
                _onError(error)
                throw error
            }

            val contentType = resp.headers["Content-Type"]
            when {
                contentType?.startsWith("application/json") == true -> {
                    val body = resp.stringSafe()
                    if (!body.isNullOrEmpty()) {
                        runCatching { McpJson.decodeFromString<JSONRPCMessage>(body) }
                            .onSuccess { _onMessage(it) }
                            .onFailure(_onError)
                    }
                }

                contentType?.startsWith("text/event-stream") == true -> {
                    handleInlineSse(
                        resp, onResumptionToken = onResumptionToken,
                        replayMessageId = if (message is JSONRPCRequest) message.id else null
                    )
                }

                else -> {
                    val body = resp.stringSafe().orEmpty()
                    if (contentType == null && body.isBlank()) return

                    val ct = contentType ?: "<none>"
                    val error = StreamableHttpError(-1, "Unexpected content type: $ct")
                    _onError(error)
                    throw error
                }
            }
        }
    }

    override suspend fun close() {
        if (!initialized.load()) return // Already closed or never started
        Logger.d(TAG) { "close: Client transport closing." }

        try {
            // Try to terminate session if we have one
            terminateSession()

            sseJob?.cancelAndJoin()
            scope.cancel()
        } catch (_: Exception) {
            // Ignore errors during cleanup
        } finally {
            initialized.store(false)
            _onClose()
        }
    }

    /**
     * Terminates the current session by sending a DELETE request to the server.
     */
    suspend fun terminateSession() {
        if (sessionId == null) return
        Logger.d(TAG) { "terminateSession: Terminating session: $sessionId" }

        val request = HttpRequestBuilder().apply {
            url(this@StreamableHttpClientTransport.url)
            applyCommonHeaders()
        }

        val response = client.delete(request)

        response.let { resp ->
            // 405 means server doesn't support explicit session termination
            if (!resp.status.isSuccess() && resp.status.value != 405) {
                val error = StreamableHttpError(
                    resp.status.value,
                    "Failed to terminate session: ${resp.status.description}"
                )
                Logger.e(TAG, error) { "Failed to terminate session" }
                _onError(error)
                throw error
            }
        }

        sessionId = null
        lastEventId = null
        Logger.d(TAG) { "Session terminated successfully" }
    }

    private suspend fun startSseSession(
        resumptionToken: String? = null,
        replayMessageId: RequestId? = null,
        onResumptionToken: ((String) -> Unit)? = null
    ) {
        sseJob?.cancelAndJoin()

        Logger.d(TAG) { "startSseSession: Client attempting to start SSE session at url: $url" }

        val request = HttpRequestBuilder().apply {
            url(this@StreamableHttpClientTransport.url)
            method = HttpMethod.Get
            applyCommonHeaders()
            header("Accept", "text/event-stream")
            (resumptionToken ?: lastEventId)?.let {
                header(MCP_RESUMPTION_TOKEN_HEADER, it)
            }
        }

        client.sse({ takeFrom(request) }) {
            Logger.d(TAG) { "startSseSession: Client SSE session started successfully." }

            try {
                sseJob = incoming
                    .onEach { event ->
                        event.id?.let {
                            lastEventId = it
                            onResumptionToken?.invoke(it)
                        }
                        Logger.d(TAG) { "collectSse: Client received SSE event: event=${event.event}, data=${event.data}, id=${event.id}" }

                        when (event.event) {
                            null, "message" -> {
                                if (!event.data.isNullOrEmpty()) {
                                    runCatching { McpJson.decodeFromString<JSONRPCMessage>(event.data!!) }
                                        .onSuccess { msg ->
                                            scope.launch {
                                                if (replayMessageId != null && msg is JSONRPCResponse) {
                                                    _onMessage(msg.copy(id = replayMessageId))
                                                } else {
                                                    _onMessage(msg)
                                                }
                                            }
                                        }
                                        .onFailure(_onError)
                                }
                            }

                            "error" -> _onError(StreamableHttpError(null, event.data))
                        }
                    }
                    .catch { throwable ->
                        Logger.e(TAG, throwable) { "SSE flow error" }
                        _onError(throwable)
                    }
                    .launchIn(scope)
            } catch (e: Exception) {
                if (call.response.status.value == 405) {
                    Logger.i(TAG) { "startSseSession: Server returned 405 for GET/SSE stream disabled." }
                    return@sse
                }
                _onError(e)
            } finally {
                Logger.d(TAG) { "startSseSession: SSE connection closed" }
            }
        }
    }

    private fun HttpRequestBuilder.applyCommonHeaders() {
        sessionId?.let { header(MCP_SESSION_ID_HEADER, it) }
        protocolVersion?.let { header(MCP_PROTOCOL_VERSION_HEADER, it) }
        this@StreamableHttpClientTransport.headers.forEach { (name, value) ->
            header(name, value)
        }
    }


    private suspend fun handleInlineSse(
        response: HttpResponse,
        replayMessageId: RequestId?,
        onResumptionToken: ((String) -> Unit)?
    ) {
        Logger.d(TAG) { "handleInlineSse: Handling inline SSE from POST response" }
        val body = response.bodyAsChannel()

        val sb = StringBuilder()
        var id: String? = null
        var eventName: String? = null

        fun dispatch(data: String) {
            id?.let {
                lastEventId = it
                onResumptionToken?.invoke(it)
            }
            if (eventName == null || eventName == "message") {
                runCatching { McpJson.decodeFromString<JSONRPCMessage>(data) }
                    .onSuccess { msg ->
                        scope.launch {
                            if (replayMessageId != null && msg is JSONRPCResponse) {
                                _onMessage(msg.copy(id = replayMessageId))
                            } else {
                                _onMessage(msg)
                            }
                        }
                    }
                    .onFailure(_onError)
            }
            // reset
            id = null
            eventName = null
            sb.clear()
        }

        while (!body.exhausted()) {
            val line = body.readUTF8Line() ?: break
            if (line.isEmpty()) {
                dispatch(sb.toString())
                continue
            }
            when {
                line.startsWith("id:") -> id = line.substringAfter("id:").trim()
                line.startsWith("event:") -> eventName = line.substringAfter("event:").trim()
                line.startsWith("data:") -> sb.append(line.substringAfter("data:").trim())
            }
        }
    }
}
