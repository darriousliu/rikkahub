package me.rerere.rikkahub.web

import kotlinx.coroutines.CoroutineScope

fun createIosWebServerRuntime(scope: CoroutineScope): WebServerRuntime =
    WebServerManager(
        host = UnavailableWebServerHost(),
        appScope = scope,
    )
