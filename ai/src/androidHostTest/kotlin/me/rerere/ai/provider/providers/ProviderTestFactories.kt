package me.rerere.ai.provider.providers

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

private val providerTestHttpClient = HttpClient(OkHttp)

internal fun googleProviderForTest(): GoogleProvider = GoogleProvider(providerTestHttpClient)

internal fun claudeProviderForTest(): ClaudeProvider = ClaudeProvider(providerTestHttpClient)

internal fun openAIProviderForTest(): OpenAIProvider = OpenAIProvider(providerTestHttpClient)
