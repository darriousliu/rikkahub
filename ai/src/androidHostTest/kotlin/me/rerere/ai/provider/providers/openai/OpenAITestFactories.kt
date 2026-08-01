package me.rerere.ai.provider.providers.openai

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import me.rerere.ai.util.KeyRoulette

private val openAITestHttpClient = HttpClient(OkHttp)

internal fun chatCompletionsApiForTest(): ChatCompletionsAPI =
    ChatCompletionsAPI(openAITestHttpClient, KeyRoulette.default())

internal fun responseApiForTest(): ResponseAPI = ResponseAPI(openAITestHttpClient)
