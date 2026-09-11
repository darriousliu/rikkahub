package me.rerere.rikkahub.web

import kotlinx.coroutines.flow.StateFlow

/** Android routes commands through its foreground service; other hosts use the manager directly. */
interface WebServerRuntime {
    val state: StateFlow<WebServerState>

    fun start(port: Int, localhostOnly: Boolean)

    fun stop()
}
