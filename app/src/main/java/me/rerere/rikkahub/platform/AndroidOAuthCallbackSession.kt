package me.rerere.rikkahub.platform

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.event.AppEvent
import me.rerere.rikkahub.data.event.AppEventBus

class AndroidOAuthCallbackSessionFactory(
    private val appScope: AppScope,
    private val appEventBus: AppEventBus,
    private val uriOpener: ExternalUriOpener,
) : OAuthCallbackSessionFactory {
    override suspend fun create(): OAuthCallbackSession {
        val ready = CompletableDeferred<Unit>()
        val callbacks = Channel<OAuthCallback>(capacity = Channel.UNLIMITED)
        val collector = appScope.launch {
            appEventBus.events
                .onSubscription { ready.complete(Unit) }
                .filterIsInstance<AppEvent.McpOAuthCallback>()
                .collect { event ->
                    callbacks.send(
                        OAuthCallback(
                            state = event.state,
                            code = event.code,
                            error = event.error,
                        )
                    )
                }
        }
        try {
            ready.await()
        } catch (error: Throwable) {
            collector.cancel()
            callbacks.close()
            throw error
        }
        return AndroidOAuthCallbackSession(
            collector = collector,
            callbacks = callbacks,
            uriOpener = uriOpener,
        )
    }
}

private class AndroidOAuthCallbackSession(
    private val collector: Job,
    private val callbacks: Channel<OAuthCallback>,
    private val uriOpener: ExternalUriOpener,
) : OAuthCallbackSession {
    override val redirectUri: String = REDIRECT_URI

    override suspend fun authorize(
        authorizationUri: String,
        expectedState: String,
    ): OAuthCallback {
        withContext(Dispatchers.Main.immediate) {
            uriOpener.open(authorizationUri).getOrThrow()
        }
        while (true) {
            val callback = callbacks.receive()
            if (callback.state == expectedState) return callback
        }
    }

    override suspend fun close() {
        collector.cancel()
        callbacks.close()
    }

    private companion object {
        const val REDIRECT_URI = "rikkahub://mcp-oauth-callback"
    }
}
