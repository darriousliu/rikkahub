package me.rerere.rikkahub.web

import kotlinx.coroutines.CoroutineScope
import me.rerere.rikkahub.platform.JvmJmDnsServiceRegistrar

fun createJvmWebServerRuntime(scope: CoroutineScope): WebServerRuntime =
    ControllerWebServerRuntime(
        controller = WebServerController(KtorWebServerHost { }),
        scope = scope,
        serviceRegistrar = JvmJmDnsServiceRegistrar(),
    )
