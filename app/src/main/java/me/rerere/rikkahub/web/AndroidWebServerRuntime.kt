package me.rerere.rikkahub.web

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.service.WebServerService

class AndroidWebServerRuntime(
    private val context: Context,
    private val manager: WebServerManager,
    private val settingsStore: SettingsStore,
    private val scope: CoroutineScope,
) : WebServerRuntime {
    override val state: StateFlow<WebServerManagerState>
        get() = manager.state

    override fun start(port: Int, localhostOnly: Boolean) {
        val intent = Intent(context, WebServerService::class.java).apply {
            action = WebServerService.ACTION_START
            putExtra(WebServerService.EXTRA_PORT, port)
            putExtra(WebServerService.EXTRA_LOCALHOST_ONLY, localhostOnly)
        }
        context.startForegroundService(intent)
        scope.launch {
            settingsStore.update { it.copy(webServerEnabled = true) }
        }
    }

    override fun stop() {
        val intent = Intent(context, WebServerService::class.java).apply {
            action = WebServerService.ACTION_STOP
        }
        context.startService(intent)
        scope.launch {
            settingsStore.update { it.copy(webServerEnabled = false) }
        }
    }
}
