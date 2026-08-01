package me.rerere.ai.provider.providers.openai

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.Url
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import me.rerere.ai.provider.CustomHeader
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.util.KeyRoulette
import me.rerere.ai.util.json
import me.rerere.ai.util.parseErrorDetail
import me.rerere.common.http.SseEvent

internal fun HttpRequestBuilder.configureOpenAIRequest(
    providerSetting: ProviderSetting.OpenAI,
    keyRoulette: KeyRoulette,
    customHeaders: List<CustomHeader>,
) {
    customHeaders.forEach { customHeader ->
        if (customHeader.name.isNotBlank()) header(customHeader.name, customHeader.value)
    }
    header(
        "Authorization",
        "Bearer ${keyRoulette.next(providerSetting.apiKey, providerSetting.id.toString())}",
    )
    when (Url(providerSetting.baseUrl).host) {
        "aihubmix.com" -> header("APP-Code", "DKHA9468")
        "openrouter.ai" -> {
            header("X-Title", "RikkaHub")
            header("HTTP-Referer", "https://rikka-ai.com")
        }
    }
}

internal fun JsonObject.toOpenAIJsonContent(): TextContent =
    TextContent(
        text = json.encodeToString(this),
        contentType = ContentType.Application.Json.withParameter("charset", "utf-8"),
    )

internal fun SseEvent.Failure.toOpenAIStreamException(): Throwable? {
    throwable?.let { return it }
    val body = response?.body
    if (body.isNullOrBlank()) return null
    return try {
        Json.parseToJsonElement(body).parseErrorDetail()
    } catch (error: Throwable) {
        error
    }
}

internal class OpenAIStreamCompleted : CancellationException("OpenAI stream completed")
