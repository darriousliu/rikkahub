package me.rerere.rikkahub.platform

import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.CompletableDeferred

public class JvmOAuthCallbackSessionFactory(
    private val uriOpener: ExternalUriOpener = JvmExternalUriOpener(),
) : OAuthCallbackSessionFactory {
    override suspend fun create(): OAuthCallbackSession {
        val callback = CompletableDeferred<OAuthCallback>()
        val server = embeddedServer(CIO, host = LOOPBACK_HOST, port = 0) {
            routing {
                get(CALLBACK_PATH) {
                    callback.complete(
                        OAuthCallback(
                            state = call.request.queryParameters["state"],
                            code = call.request.queryParameters["code"],
                            error = call.request.queryParameters["error"],
                        )
                    )
                    call.respondText("Authorization complete. You can close this window.")
                }
            }
        }
        try {
            server.start(wait = false)
            val port = server.engine.resolvedConnectors().single().port
            return JvmOAuthCallbackSession(
                server = server,
                callback = callback,
                uriOpener = uriOpener,
                redirectUri = "http://$LOOPBACK_HOST:$port$CALLBACK_PATH",
            )
        } catch (error: Throwable) {
            runCatching { server.stop(gracePeriodMillis = 100, timeoutMillis = 1_000) }
            throw error
        }
    }

    private companion object {
        const val CALLBACK_PATH = "/oauth/callback"
        const val LOOPBACK_HOST = "127.0.0.1"
    }
}

private class JvmOAuthCallbackSession(
    private val server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>,
    private val callback: CompletableDeferred<OAuthCallback>,
    private val uriOpener: ExternalUriOpener,
    override val redirectUri: String,
) : OAuthCallbackSession {
    private var closed = false

    override suspend fun authorize(
        authorizationUri: String,
        expectedState: String,
    ): OAuthCallback {
        uriOpener.open(authorizationUri).getOrThrow()
        return callback.await()
    }

    override suspend fun close() {
        if (closed) return
        closed = true
        callback.cancel()
        server.stop(gracePeriodMillis = 100, timeoutMillis = 1_000)
    }
}
