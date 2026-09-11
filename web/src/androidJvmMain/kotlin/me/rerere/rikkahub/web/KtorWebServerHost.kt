package me.rerere.rikkahub.web

import io.ktor.server.application.Application
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import java.net.ServerSocket

class KtorWebServerHost(
    private val module: suspend Application.() -> Unit,
) : WebServerHost {
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

    override fun isPortAvailable(port: Int): Boolean {
        return try {
            ServerSocket(port).use { true }
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun start(port: Int, host: String) {
        server = startWebServer(port = port, host = host, module = module).start(wait = false)
    }

    override suspend fun stop(gracePeriodMillis: Long, timeoutMillis: Long) {
        server?.stop(gracePeriodMillis, timeoutMillis)
        server = null
    }
}
