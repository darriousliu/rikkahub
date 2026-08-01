package me.rerere.ai.provider.providers

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

internal fun claudeProviderForTest(): ClaudeProvider = ClaudeProvider(HttpClient(OkHttp))
