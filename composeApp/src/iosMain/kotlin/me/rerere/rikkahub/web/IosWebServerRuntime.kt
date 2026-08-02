package me.rerere.rikkahub.web

import kotlinx.coroutines.CoroutineScope

fun createIosWebServerRuntime(scope: CoroutineScope): WebServerRuntime =
    ControllerWebServerRuntime(
        controller = WebServerController(UnavailableWebServerHost()),
        scope = scope,
    )
