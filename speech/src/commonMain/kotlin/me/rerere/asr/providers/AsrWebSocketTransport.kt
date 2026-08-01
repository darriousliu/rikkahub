package me.rerere.asr.providers

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.headers
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi

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

    fun close()
}

class KtorAsrWebSocketTransport(
    private val client: HttpClient,
) : AsrWebSocketTransport {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun connect(
        url: String,
        headers: Map<String, String>,
        listener: AsrWebSocketListener,
    ): AsrWebSocketSession {
        val session = KtorAsrWebSocketSession()
        val requestHeaders = headers
        val webSocketUrl = url.toWebSocketUrl()
        scope.launch {
            try {
                client.webSocket(
                    urlString = webSocketUrl,
                    request = {
                        headers {
                            requestHeaders.forEach { (name, value) -> append(name, value) }
                        }
                    },
                ) {
                    val writerJob = launch { session.writeTo(this@webSocket) }
                    try {
                        listener.onOpen(session)
                        for (frame in incoming) {
                            when (frame) {
                                is Frame.Text -> listener.onText(session, frame.readText())
                                is Frame.Binary -> listener.onBinary(session, frame.readBytes())
                                else -> Unit
                            }
                        }
                        val reason = closeReason.await()
                        listener.onClosed(
                            session = session,
                            code = reason?.code?.toInt() ?: CloseReason.Codes.NORMAL.code.toInt(),
                            reason = reason?.message.orEmpty(),
                        )
                    } finally {
                        writerJob.cancelAndJoin()
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (!session.isClosing) {
                    listener.onFailure(session, error)
                }
            } finally {
                session.finish()
            }
        }
        return session
    }

    override fun close() {
        scope.cancel()
    }
}

private fun String.toWebSocketUrl(): String = when {
    startsWith("https://", ignoreCase = true) -> "wss://${substring(8)}"
    startsWith("http://", ignoreCase = true) -> "ws://${substring(7)}"
    else -> this
}

@OptIn(ExperimentalAtomicApi::class)
private class KtorAsrWebSocketSession : AsrWebSocketSession {
    private val outgoing = Channel<QueuedFrame>(Channel.UNLIMITED)
    private val queuedBytes = AtomicLong(0L)
    private val acceptingFrames = AtomicBoolean(true)

    override val queueSize: Long
        get() = queuedBytes.load()

    internal val isClosing: Boolean
        get() = !acceptingFrames.load()

    override fun send(text: String): Boolean = enqueue(
        QueuedFrame.Text(text),
        text.encodeToByteArray().size.toLong(),
    )

    override fun send(bytes: ByteArray): Boolean = enqueue(
        QueuedFrame.Binary(bytes.copyOf()),
        bytes.size.toLong(),
    )

    override fun close(code: Int, reason: String): Boolean {
        if (!acceptingFrames.compareAndSet(true, false)) return false
        val accepted = outgoing.trySend(QueuedFrame.Close(code, reason)).isSuccess
        outgoing.close()
        return accepted
    }

    internal suspend fun writeTo(socket: WebSocketSession) {
        for (frame in outgoing) {
            when (frame) {
                is QueuedFrame.Text -> {
                    socket.send(frame.value)
                    removeQueuedBytes(frame.value.encodeToByteArray().size.toLong())
                }

                is QueuedFrame.Binary -> {
                    socket.send(Frame.Binary(fin = true, data = frame.value))
                    removeQueuedBytes(frame.value.size.toLong())
                }

                is QueuedFrame.Close -> {
                    socket.close(CloseReason(frame.code.toShort(), frame.reason))
                    break
                }
            }
        }
    }

    internal fun finish() {
        acceptingFrames.store(false)
        queuedBytes.store(0L)
        outgoing.close()
    }

    private fun enqueue(frame: QueuedFrame, byteCount: Long): Boolean {
        if (!acceptingFrames.load()) return false
        updateQueuedBytes { it + byteCount }
        if (outgoing.trySend(frame).isSuccess) {
            return true
        } else {
            updateQueuedBytes { (it - byteCount).coerceAtLeast(0L) }
            return false
        }
    }

    private fun removeQueuedBytes(byteCount: Long) {
        updateQueuedBytes { (it - byteCount).coerceAtLeast(0L) }
    }

    private fun updateQueuedBytes(transform: (Long) -> Long) {
        while (true) {
            val snapshot = queuedBytes.load()
            if (queuedBytes.compareAndSet(snapshot, transform(snapshot))) return
        }
    }
}

private sealed interface QueuedFrame {
    data class Text(val value: String) : QueuedFrame

    data class Binary(val value: ByteArray) : QueuedFrame

    data class Close(val code: Int, val reason: String) : QueuedFrame
}
