package me.rerere.asr.providers

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString

interface AsrWebSocketSession {
    val queueSize: Long

    fun send(text: String): Boolean

    fun send(bytes: ByteArray): Boolean

    fun close(code: Int, reason: String): Boolean
}

interface AsrWebSocketListener {
    fun onOpen(session: AsrWebSocketSession)

    fun onText(session: AsrWebSocketSession, text: String)

    fun onBinary(session: AsrWebSocketSession, bytes: ByteArray)

    fun onFailure(session: AsrWebSocketSession, error: Throwable)

    fun onClosed(session: AsrWebSocketSession, code: Int, reason: String)
}

interface AsrWebSocketTransport {
    fun connect(
        url: String,
        headers: Map<String, String>,
        listener: AsrWebSocketListener,
    ): AsrWebSocketSession
}

class OkHttpAsrWebSocketTransport(
    private val client: OkHttpClient,
) : AsrWebSocketTransport {
    override fun connect(
        url: String,
        headers: Map<String, String>,
        listener: AsrWebSocketListener,
    ): AsrWebSocketSession {
        val request = Request.Builder()
            .url(url)
            .apply {
                headers.forEach { (name, value) -> addHeader(name, value) }
            }
            .build()
        lateinit var session: OkHttpAsrWebSocketSession
        val socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                listener.onOpen(session)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                listener.onText(session, text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                listener.onBinary(session, bytes.toByteArray())
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                listener.onFailure(session, t)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                listener.onClosed(session, code, reason)
            }
        })
        session = OkHttpAsrWebSocketSession(socket)
        return session
    }
}

private class OkHttpAsrWebSocketSession(
    private val socket: WebSocket,
) : AsrWebSocketSession {
    override val queueSize: Long
        get() = socket.queueSize()

    override fun send(text: String): Boolean = socket.send(text)

    override fun send(bytes: ByteArray): Boolean = socket.send(bytes.toByteString())

    override fun close(code: Int, reason: String): Boolean = socket.close(code, reason)
}
