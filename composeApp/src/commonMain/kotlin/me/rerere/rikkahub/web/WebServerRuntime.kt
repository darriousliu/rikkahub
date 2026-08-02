package me.rerere.rikkahub.web

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.rerere.rikkahub.platform.DEFAULT_SERVICE_NAME
import me.rerere.rikkahub.platform.RegisteredServiceInfo
import me.rerere.rikkahub.platform.ServiceRegistrar
import me.rerere.rikkahub.platform.ServiceRegistration

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

/**
 * Shared lifecycle adapter used by non-Android shells. Android keeps its foreground-service adapter.
 */
class ControllerWebServerRuntime(
    private val controller: WebServerController,
    private val scope: CoroutineScope,
    private val serviceRegistrar: ServiceRegistrar? = null,
) : WebServerRuntime {
    private val mutableState = MutableStateFlow(WebServerManagerState())
    private var operation: Job? = null

    override val state: StateFlow<WebServerManagerState> = mutableState.asStateFlow()

    override fun start(port: Int, localhostOnly: Boolean) {
        if (state.value.isRunning || state.value.isLoading) return

        val baseState = WebServerManagerState(
            isLoading = true,
            port = port,
            localhostOnly = localhostOnly,
        )
        mutableState.value = baseState
        operation = scope.launch {
            val lifecycle = controller.start(
                WebServerConfig(
                    port = port,
                    serviceName = baseState.serviceName,
                    localhostOnly = localhostOnly,
                )
            )
            publishStartResult(baseState, lifecycle)
        }
    }

    override fun stop() {
        if (!state.value.isRunning && !state.value.isLoading) return

        operation?.cancel()
        mutableState.value = state.value.copy(
            isRunning = false,
            isLoading = true,
            hostname = null,
            address = null,
            error = null,
        )
        operation = scope.launch {
            val lifecycle = controller.stop()
            serviceRegistrar?.unregister()
            mutableState.value = state.value.copy(
                isRunning = false,
                isLoading = false,
                error = (lifecycle as? WebServerState.Failed)?.message,
            )
        }
    }

    private suspend fun publishStartResult(
        baseState: WebServerManagerState,
        lifecycle: WebServerState,
    ) {
        when (lifecycle) {
            is WebServerState.Running -> {
                mutableState.value = baseState.copy(
                    isRunning = true,
                    isLoading = false,
                    port = lifecycle.endpoint.port,
                )
                if (!lifecycle.config.localhostOnly) {
                    serviceRegistrar?.register(
                        ServiceRegistration(
                            port = lifecycle.endpoint.port,
                            serviceName = lifecycle.config.serviceName,
                        )
                    )?.onSuccess(::publishServiceInfo)
                }
            }

            is WebServerState.Failed -> {
                mutableState.value = baseState.copy(
                    isLoading = false,
                    error = lifecycle.message,
                )
            }

            is WebServerState.Unavailable -> {
                mutableState.value = baseState.copy(
                    isLoading = false,
                    error = lifecycle.reason,
                )
            }

            is WebServerState.Starting,
            WebServerState.Stopped,
            -> mutableState.value = baseState.copy(isLoading = false)
        }
    }

    private fun publishServiceInfo(info: RegisteredServiceInfo) {
        mutableState.value = state.value.copy(
            serviceName = info.serviceName,
            hostname = info.hostname,
            address = info.address,
        )
    }
}
