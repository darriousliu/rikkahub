package me.rerere.rikkahub.web

import kotlinx.coroutines.CoroutineScope
import me.rerere.rikkahub.platform.JvmJmDnsServiceRegistrar

fun createJvmWebServerRuntime(scope: CoroutineScope): WebServerRuntime =
    WebServerManager(
        host = KtorWebServerHost { },
        appScope = scope,
        nsdRegistrar = JvmJmDnsServiceRegistrar(),
    )
