package me.rerere.rikkahub.web

import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import java.net.HttpURLConnection
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.runBlocking

class KtorWebServerHostTest {
    @Test
    fun servesLoopbackRouteOnEphemeralPort() = runBlocking {
        val controller = WebServerController(
            KtorWebServerHost {
                routing {
                    get("/api/health") {
                        call.respondText("ok")
                    }
                }
            }
        )

        try {
            val running = assertIs<WebServerState.Running>(
                controller.start(WebServerConfig(port = 0, localhostOnly = true))
            )
            val connection = URI(
                "http://127.0.0.1:${running.endpoint.port}/api/health"
            ).toURL().openConnection() as HttpURLConnection

            assertEquals(200, connection.responseCode)
            assertEquals("ok", connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            controller.stop()
        }
    }
}
