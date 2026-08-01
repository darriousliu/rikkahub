package me.rerere.ai.provider.providers.openai

import me.rerere.ai.util.KeyRoulette
import okhttp3.OkHttpClient

internal fun chatCompletionsApiForTest(): ChatCompletionsAPI =
    ChatCompletionsAPI(OkHttpClient(), KeyRoulette.default())

internal fun responseApiForTest(): ResponseAPI = ResponseAPI(OkHttpClient())
