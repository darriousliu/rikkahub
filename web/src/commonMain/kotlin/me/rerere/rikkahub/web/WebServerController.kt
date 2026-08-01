package me.rerere.rikkahub.web

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class WebServerConfig(
    val port: Int = 8080,
    val serviceName: String = "rikkahub",
    val localhostOnly: Boolean = false,
) {
    init {
        require(port in 0..65535) { "Web server port must be between 0 and 65535" }
        require(serviceName.isNotBlank()) { "Web server service name must not be blank" }
    }
}

data class WebServerEndpoint(
    val host: String,
    val port: Int,
)

sealed interface WebServerState {
    data object Stopped : WebServerState

    data class Starting(val config: WebServerConfig) : WebServerState

    data class Running(
        val config: WebServerConfig,
        val endpoint: WebServerEndpoint,
    ) : WebServerState

    data class Unavailable(val reason: String) : WebServerState

    data class Failed(
        val config: WebServerConfig?,
        val message: String,
    ) : WebServerState
}

sealed interface WebServerHostStartResult {
    data class Running(val endpoint: WebServerEndpoint) : WebServerHostStartResult

    data class Unavailable(val reason: String) : WebServerHostStartResult
}

/** Platform hosting boundary. Implementations keep Ktor engine types out of common code. */
interface WebServerHost {
    suspend fun start(config: WebServerConfig): WebServerHostStartResult

    suspend fun stop()
}

/** Serializes web server lifecycle operations and exposes only platform-independent state. */
class WebServerController(
    private val host: WebServerHost,
) {
    private val lifecycleMutex = Mutex()
    private val _state = MutableStateFlow<WebServerState>(WebServerState.Stopped)

    val state: StateFlow<WebServerState> = _state.asStateFlow()

    suspend fun start(config: WebServerConfig = WebServerConfig()): WebServerState = lifecycleMutex.withLock {
        val current = _state.value
        if (current is WebServerState.Running) return@withLock current

        startLocked(config)
    }

    suspend fun stop(): WebServerState = lifecycleMutex.withLock {
        stopLocked()
    }

    suspend fun restart(config: WebServerConfig = currentConfig()): WebServerState = lifecycleMutex.withLock {
        stopLocked()
        startLocked(config)
    }

    private suspend fun startLocked(config: WebServerConfig): WebServerState {
        _state.value = WebServerState.Starting(config)
        val next = try {
            when (val result = host.start(config)) {
                is WebServerHostStartResult.Running -> WebServerState.Running(config, result.endpoint)
                is WebServerHostStartResult.Unavailable -> WebServerState.Unavailable(result.reason)
            }
        } catch (error: CancellationException) {
            _state.value = WebServerState.Stopped
            throw error
        } catch (error: Throwable) {
            WebServerState.Failed(config, error.message ?: "Web server failed to start")
        }
        _state.value = next
        return next
    }

    private suspend fun stopLocked(): WebServerState {
        val current = _state.value
        if (current is WebServerState.Stopped || current is WebServerState.Unavailable) return current

        val next = try {
            host.stop()
            WebServerState.Stopped
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            WebServerState.Failed(current.configOrNull(), error.message ?: "Web server failed to stop")
        }
        _state.value = next
        return next
    }

    private fun currentConfig(): WebServerConfig = _state.value.configOrNull() ?: WebServerConfig()
}

private fun WebServerState.configOrNull(): WebServerConfig? = when (this) {
    is WebServerState.Starting -> config
    is WebServerState.Running -> config
    is WebServerState.Failed -> config
    is WebServerState.Stopped,
    is WebServerState.Unavailable,
    -> null
}
