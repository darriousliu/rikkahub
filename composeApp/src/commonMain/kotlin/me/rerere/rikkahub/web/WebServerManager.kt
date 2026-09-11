package me.rerere.rikkahub.web

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.rerere.rikkahub.platform.DEFAULT_SERVICE_NAME
import me.rerere.rikkahub.platform.ServiceRegistrar
import me.rerere.rikkahub.platform.ServiceRegistration
import me.rerere.common.logging.RikkaLog as Log

private const val TAG = "WebServerManager"
private const val HOST_ALL_INTERFACES = "0.0.0.0"
private const val HOST_LOOPBACK = "127.0.0.1"

data class WebServerState(
    val isRunning: Boolean = false,
    val isLoading: Boolean = false,
    val port: Int = 8080,
    val serviceName: String = DEFAULT_SERVICE_NAME,
    val localhostOnly: Boolean = false,
    val hostname: String? = null,
    val address: String? = null,
    val error: String? = null,
)

class WebServerManager(
    private val appScope: CoroutineScope,
    private val host: WebServerHost,
    private val nsdRegistrar: ServiceRegistrar? = null,
) : WebServerRuntime {
    private var server: WebServerHost? = null

    private val _state = MutableStateFlow(WebServerState())
    override val state: StateFlow<WebServerState> = _state.asStateFlow()

    override fun start(port: Int, localhostOnly: Boolean) {
        start(port, DEFAULT_SERVICE_NAME, localhostOnly)
    }

    fun start(
        port: Int = 8080,
        serviceName: String = DEFAULT_SERVICE_NAME,
        localhostOnly: Boolean = false,
    ) {
        if (server != null) {
            Log.w(TAG, "Server already running")
            return
        }

        appScope.launch {
            // 仅本机模式绑定回环地址
            val host = if (localhostOnly) HOST_LOOPBACK else HOST_ALL_INTERFACES
            val baseState = WebServerState(
                port = port,
                serviceName = serviceName,
                localhostOnly = localhostOnly,
            )
            try {
                _state.value = _state.value.copy(isLoading = true)
                Log.i(TAG, "Starting web server on $host:$port")
                if (!this@WebServerManager.host.isPortAvailable(port)) {
                    Log.w(TAG, "Port $port is already in use")
                    _state.value = baseState.copy(error = "Port $port is already in use")
                    return@launch
                }
                this@WebServerManager.host.start(port, host)
                server = this@WebServerManager.host

                _state.value = baseState.copy(isRunning = true)
                // 仅局域网模式注册 mDNS
                if (!localhostOnly) {
                    runCatching {
                        nsdRegistrar?.register(
                            ServiceRegistration(port = port, serviceName = serviceName),
                        )?.getOrThrow()?.let { info ->
                            _state.value = _state.value.copy(
                                serviceName = info.serviceName,
                                hostname = info.hostname,
                                address = info.address,
                            )
                        }
                    }.onFailure {
                        Log.w(TAG, "NSD register failed", it)
                    }
                }
                Log.i(TAG, "Web server started successfully on $host:$port")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start web server", e)
                _state.value = baseState.copy(error = e.message)
            }
        }
    }

    fun reportError(message: String) {
        _state.value = _state.value.copy(isRunning = false, isLoading = false, error = message)
    }

    override fun stop() {
        _state.value =
            _state.value.copy(isRunning = false, isLoading = true, hostname = null, address = null, error = null)
        appScope.launch {
            try {
                Log.i(TAG, "Stopping web server")
                server?.stop(1000, 2000)
                server = null
                runCatching {
                    nsdRegistrar?.unregister()?.getOrThrow()
                }.onFailure {
                    Log.w(TAG, "NSD unregister failed", it)
                }
                _state.value = _state.value.copy(isLoading = false)
                Log.i(TAG, "Web server stopped")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop web server", e)
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun restart(
        port: Int = _state.value.port,
        serviceName: String = _state.value.serviceName,
        localhostOnly: Boolean = _state.value.localhostOnly,
    ) {
        stop()
        start(port, serviceName, localhostOnly)
    }
}
