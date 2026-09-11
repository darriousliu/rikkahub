package me.rerere.rikkahub.web

import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class KtorWebServerHostTest {
    @Test
    fun servesLoopbackRouteAndReleasesThePortOnStop() = runBlocking {
        val host = KtorWebServerHost {
            routing {
                get("/api/health") { call.respondText("ok") }
            }
        }
        val port = ServerSocket(0).use { it.localPort }
        assertTrue(host.isPortAvailable(port))
        try {
            host.start(port, "127.0.0.1")
            val connection = URI("http://127.0.0.1:$port/api/health").toURL()
                .openConnection() as HttpURLConnection
            connection.connectTimeout = 3_000
            connection.readTimeout = 3_000
            try {
                assertEquals(200, connection.responseCode)
                assertEquals("ok", connection.inputStream.bufferedReader().use { it.readText() })
            } finally {
                connection.disconnect()
            }
            Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 1_000) }
        } finally {
            host.stop(1_000, 2_000)
        }
        assertFailsWith<IOException> {
            Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 1_000) }
        }
        host.stop(1_000, 2_000)
    }

    @Test
    fun usesTheOriginalServerSocketAvailabilityCheck() {
        val host = KtorWebServerHost { }
        ServerSocket(0).use { occupied ->
            assertFalse(host.isPortAvailable(occupied.localPort))
        }
        assertFalse(host.isPortAvailable(-1))
        assertFalse(host.isPortAvailable(65536))
        assertTrue(host.isPortAvailable(0))
    }
}
