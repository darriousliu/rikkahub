package me.rerere.rikkahub.shared

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import me.rerere.common.logging.RequestLoggingPlugin

internal actual fun createAppHttpClient(): HttpClient = HttpClient(Darwin) {
    install(HttpTimeout) {
        // Darwin exposes an inactivity timeout, but no separate connect/write timeouts.
        socketTimeoutMillis = 600_000
    }
    install(RequestLoggingPlugin)
}
