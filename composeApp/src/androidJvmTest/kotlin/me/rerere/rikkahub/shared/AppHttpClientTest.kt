package me.rerere.rikkahub.shared

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class AppHttpClientTest {
    @Test
    fun responseAfterTwelveSecondsOfSilenceDoesNotUseTheEngineDefaultTimeout() = runBlocking {
        withServer { socket ->
            socket.getOutputStream().apply {
                write("HTTP/1.1 200 OK\r\nContent-Length: 4\r\nConnection: close\r\n\r\n".toByteArray())
                flush()
                Thread.sleep(12_000)
                write("done".toByteArray())
                flush()
            }
        }.use { server ->
            createAppHttpClient().use { client ->
                withTimeout(20_000) {
                    assertEquals("done", client.get(server.url).bodyAsText())
                }
            }
        }
    }

    @Test
    fun waitingForAResponseRemainsCancellable() = runBlocking {
        val ready = CompletableDeferred<Unit>()
        val disconnected = CompletableDeferred<Boolean>()
        withServer { socket ->
            socket.soTimeout = 3_000
            socket.getOutputStream().apply {
                write("HTTP/1.1 200 OK\r\nContent-Length: 4\r\nConnection: close\r\n\r\n".toByteArray())
                flush()
            }
            ready.complete(Unit)
            disconnected.complete(socket.getInputStream().read() == -1)
        }.use { server ->
            createAppHttpClient().use { client ->
                val request = async { client.get(server.url).bodyAsText() }
                withTimeout(3_000) { ready.await() }
                withTimeout(3_000) { request.cancelAndJoin() }
                assertTrue(request.isCancelled)
                assertTrue(withTimeout(3_000) { disconnected.await() })
            }
        }
    }

    private fun withServer(respond: (Socket) -> Unit) = LocalServer(respond)

    private class LocalServer(respond: (Socket) -> Unit) : AutoCloseable {
        private val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val url = "http://127.0.0.1:${server.localPort}/"
        private var socket: Socket? = null
        private val worker = thread(isDaemon = true, name = "app-http-client-test") {
            try {
                server.accept().use { client ->
                    socket = client
                    val input = client.getInputStream()
                    var suffix = ""
                    while (!suffix.endsWith("\r\n\r\n")) {
                        val value = input.read()
                        check(value >= 0)
                        suffix = (suffix + value.toChar()).takeLast(4)
                    }
                    respond(client)
                }
            } catch (_: InterruptedException) {
                // Test teardown interrupts the server's delayed response.
            }
        }

        override fun close() {
            server.close()
            socket?.close()
            worker.interrupt()
            worker.join(1_000)
        }
    }
}
