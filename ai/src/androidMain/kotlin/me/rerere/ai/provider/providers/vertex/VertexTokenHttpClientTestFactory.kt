package me.rerere.ai.provider.providers.vertex

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

internal fun vertexTokenHttpClientForTest(): HttpClient = HttpClient(OkHttp)
