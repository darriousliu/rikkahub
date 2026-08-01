package me.rerere.rikkahub.web

import kotlinx.coroutines.flow.StateFlow
import me.rerere.rikkahub.platform.DEFAULT_SERVICE_NAME

data class WebServerManagerState(
    val isRunning: Boolean = false,
    val isLoading: Boolean = false,
    val port: Int = 8080,
    val serviceName: String = DEFAULT_SERVICE_NAME,
    val localhostOnly: Boolean = false,
    val hostname: String? = null,
    val address: String? = null,
    val error: String? = null,
)

/** Shared UI boundary for platform-owned Web Server lifecycle handling. */
interface WebServerRuntime {
    val state: StateFlow<WebServerManagerState>

    fun start(port: Int, localhostOnly: Boolean)

    fun stop()
}
