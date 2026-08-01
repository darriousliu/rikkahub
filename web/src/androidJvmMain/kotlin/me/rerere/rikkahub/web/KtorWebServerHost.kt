package me.rerere.rikkahub.web

import io.ktor.server.application.Application
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer

class KtorWebServerHost(
    private val module: suspend Application.() -> Unit,
) : WebServerHost {
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    private var endpoint: WebServerEndpoint? = null

    override suspend fun start(config: WebServerConfig): WebServerHostStartResult {
        endpoint?.let { return WebServerHostStartResult.Running(it) }

        val host = if (config.localhostOnly) LOOPBACK_HOST else ALL_INTERFACES_HOST
        val candidate = startWebServer(port = config.port, host = host, module = module)
        try {
            candidate.start(wait = false)
            val resolvedPort = candidate.engine.resolvedConnectors().single().port
            val resolvedEndpoint = WebServerEndpoint(host = host, port = resolvedPort)
            server = candidate
            endpoint = resolvedEndpoint
            return WebServerHostStartResult.Running(resolvedEndpoint)
        } catch (error: Throwable) {
            runCatching { candidate.stop(gracePeriodMillis = 100, timeoutMillis = 1_000) }
            throw error
        }
    }

    override suspend fun stop() {
        val activeServer = server ?: return
        server = null
        endpoint = null
        activeServer.stop(gracePeriodMillis = 1_000, timeoutMillis = 2_000)
    }

    private companion object {
        const val ALL_INTERFACES_HOST = "0.0.0.0"
        const val LOOPBACK_HOST = "127.0.0.1"
    }
}
