package me.rerere.rikkahub.shared

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.util.concurrent.TimeUnit
import me.rerere.common.logging.RequestLoggingPlugin
import okhttp3.OkHttpClient

internal actual fun createAppHttpClient(): HttpClient = HttpClient(OkHttp) {
    engine {
        config { applyAppTimeouts() }
    }
    install(RequestLoggingPlugin)
}

fun OkHttpClient.Builder.applyAppTimeouts(): OkHttpClient.Builder =
    connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.MINUTES)
        .writeTimeout(120, TimeUnit.SECONDS)
