package me.rerere.rikkahub.web

import android.content.Context
import me.rerere.common.logging.RikkaLog as Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.FolderRepository
import me.rerere.rikkahub.platform.AndroidJmDnsServiceRegistrar
import me.rerere.rikkahub.platform.DEFAULT_SERVICE_NAME
import me.rerere.rikkahub.platform.ServiceRegistration
import me.rerere.rikkahub.service.ChatService

private const val TAG = "WebServerManager"

data class WebServerManagerState(
    val lifecycle: WebServerState = WebServerState.Stopped,
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
    private val context: Context,
    private val appScope: AppScope,
    private val chatService: ChatService,
    private val conversationRepo: ConversationRepository,
    private val folderRepo: FolderRepository,
    private val settingsStore: SettingsStore,
    private val filesManager: FilesManager,
) {
    private val serviceRegistrar = AndroidJmDnsServiceRegistrar(context)
    private val controller = WebServerController(
        KtorWebServerHost {
            configureWebApi(context, chatService, conversationRepo, folderRepo, settingsStore, filesManager)
        }
    )

    private val _state = MutableStateFlow(WebServerManagerState())
    val state: StateFlow<WebServerManagerState> = _state.asStateFlow()
    val lifecycle: StateFlow<WebServerState> = controller.state

    fun start(
        port: Int = 8080,
        serviceName: String = DEFAULT_SERVICE_NAME,
        localhostOnly: Boolean = false,
    ) {
        if (_state.value.isRunning || _state.value.isLoading) {
            Log.w(TAG, "Server already running")
            return
        }

        appScope.launch {
            val config = WebServerConfig(
                port = port,
                serviceName = serviceName,
                localhostOnly = localhostOnly,
            )
            val baseState = WebServerManagerState(
                lifecycle = WebServerState.Starting(config),
                isLoading = true,
                port = port,
                serviceName = serviceName,
                localhostOnly = localhostOnly,
            )
            _state.value = baseState
            Log.i(TAG, "Starting web server on port $port")
            publishStartResult(baseState, controller.start(config))
        }
    }

    fun reportError(message: String) {
        _state.value = _state.value.copy(
            lifecycle = WebServerState.Failed(_state.value.lifecycle.configOrNull(), message),
            isRunning = false,
            isLoading = false,
            error = message,
        )
    }

    fun stop() {
        _state.value = _state.value.copy(
            isRunning = false,
            isLoading = true,
            hostname = null,
            address = null,
            error = null,
        )
        appScope.launch {
            try {
                Log.i(TAG, "Stopping web server")
                val lifecycle = controller.stop()
                serviceRegistrar.unregister()
                    .onFailure { Log.w(TAG, "mDNS unregister failed", it) }
                _state.value = _state.value.copy(
                    lifecycle = lifecycle,
                    isRunning = false,
                    isLoading = false,
                    error = (lifecycle as? WebServerState.Failed)?.message,
                )
                Log.i(TAG, "Web server stopped")
            } catch (error: Exception) {
                Log.e(TAG, "Failed to stop web server", error)
                _state.value = _state.value.copy(isLoading = false, error = error.message)
            }
        }
    }

    fun restart(
        port: Int = _state.value.port,
        serviceName: String = _state.value.serviceName,
        localhostOnly: Boolean = _state.value.localhostOnly,
    ) {
        appScope.launch {
            val config = WebServerConfig(port, serviceName, localhostOnly)
            serviceRegistrar.unregister()
            val baseState = WebServerManagerState(
                lifecycle = WebServerState.Starting(config),
                isLoading = true,
                port = port,
                serviceName = serviceName,
                localhostOnly = localhostOnly,
            )
            _state.value = baseState
            publishStartResult(baseState, controller.restart(config))
        }
    }

    private suspend fun publishStartResult(baseState: WebServerManagerState, lifecycle: WebServerState) {
        when (lifecycle) {
            is WebServerState.Running -> {
                _state.value = baseState.copy(
                    lifecycle = lifecycle,
                    isRunning = true,
                    isLoading = false,
                    port = lifecycle.endpoint.port,
                )
                if (!lifecycle.config.localhostOnly) {
                    serviceRegistrar.register(
                        ServiceRegistration(
                            port = lifecycle.endpoint.port,
                            serviceName = lifecycle.config.serviceName,
                        ),
                    ).onSuccess { info ->
                        _state.value = _state.value.copy(
                            serviceName = info.serviceName,
                            hostname = info.hostname,
                            address = info.address,
                        )
                    }.onFailure { Log.w(TAG, "mDNS register failed", it) }
                }
                Log.i(
                    TAG,
                    "Web server started successfully on ${lifecycle.endpoint.host}:${lifecycle.endpoint.port}",
                )
            }

            is WebServerState.Failed -> {
                Log.e(TAG, "Failed to start web server: ${lifecycle.message}")
                _state.value = baseState.copy(
                    lifecycle = lifecycle,
                    isLoading = false,
                    error = lifecycle.message,
                )
            }

            is WebServerState.Unavailable -> {
                _state.value = baseState.copy(
                    lifecycle = lifecycle,
                    isLoading = false,
                    error = lifecycle.reason,
                )
            }

            is WebServerState.Starting,
            WebServerState.Stopped,
            -> _state.value = baseState.copy(lifecycle = lifecycle, isLoading = false)
        }
    }
}

private fun WebServerState.configOrNull(): WebServerConfig? = when (this) {
    is WebServerState.Starting -> config
    is WebServerState.Running -> config
    is WebServerState.Failed -> config
    WebServerState.Stopped,
    is WebServerState.Unavailable,
    -> null
}
