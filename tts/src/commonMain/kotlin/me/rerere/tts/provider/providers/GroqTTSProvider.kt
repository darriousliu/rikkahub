package me.rerere.tts.provider.providers

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.common.PlatformContext
import me.rerere.tts.model.AudioChunk
import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.provider.TTSProvider
import me.rerere.tts.provider.TTSProviderSetting

private const val TAG = "GroqTTSProvider"

class GroqTTSProvider : TTSProvider<TTSProviderSetting.Groq> {
    private val httpClient = HttpClient {
        install(HttpTimeout) {
            requestTimeoutMillis = 120_000
        }
    }

    override fun generateSpeech(
        context: PlatformContext,
        providerSetting: TTSProviderSetting.Groq,
        request: TTSRequest
    ): Flow<AudioChunk> = flow {
        val requestBody = buildJsonObject {
            put("model", providerSetting.model)
            put("input", request.text)
            put("voice", providerSetting.voice)
            put("response_format", "wav")
        }

        Logger.i(TAG) { "generateSpeech: $requestBody" }

        val httpRequest = HttpRequestBuilder().apply {
            url("${providerSetting.baseUrl}/audio/speech")
            header("Authorization", "Bearer ${providerSetting.apiKey}")
            method = HttpMethod.Post
            contentType(ContentType.Application.Json)
            setBody(requestBody.toString())
        }

        val response = httpClient.request(httpRequest)

        if (!response.status.isSuccess()) {
            Logger.e(TAG) { "generateSpeech: ${response.status.value} ${response.status.description}" }
            val errorText = response.bodyAsText()
            Logger.e(TAG) { "generateSpeech: ${errorText}" }
            throw Exception("Groq TTS request failed: ${response.status.value} ${response.status.description}")
        }

        val audioData = response.bodyAsBytes()

        emit(
            AudioChunk(
                data = audioData,
                format = AudioFormat.WAV,
                isLast = true,
                metadata = mapOf(
                    "provider" to "groq",
                    "model" to providerSetting.model,
                    "voice" to providerSetting.voice,
                    "response_format" to "wav"
                )
            )
        )
    }
}
