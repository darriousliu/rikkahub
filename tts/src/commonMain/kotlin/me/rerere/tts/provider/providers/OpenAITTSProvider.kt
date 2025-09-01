package me.rerere.tts.provider.providers

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.common.PlatformContext
import me.rerere.tts.model.AudioChunk
import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.provider.TTSProvider
import me.rerere.tts.provider.TTSProviderSetting

private const val TAG = "OpenAITTSProvider"

class OpenAITTSProvider : TTSProvider<TTSProviderSetting.OpenAI> {
    private val httpClient = HttpClient {
        install(HttpTimeout) {
            socketTimeoutMillis = 30_000
        }
    }

    override fun generateSpeech(
        context: PlatformContext,
        providerSetting: TTSProviderSetting.OpenAI,
        request: TTSRequest
    ): Flow<AudioChunk> = flow {
        val requestBody = buildJsonObject {
            put("model", providerSetting.model)
            put("input", request.text)
            put("voice", providerSetting.voice)
            put("response_format", "mp3") // Default to MP3
        }

        Logger.i(TAG) { "generateSpeech: $requestBody" }

        val httpRequest = HttpRequestBuilder().apply {
            url("${providerSetting.baseUrl}/audio/speech")
            header("Authorization", "Bearer ${providerSetting.apiKey}")
            header("Content-Type", "application/json")
            setBody(requestBody.toString())
        }

        val response = httpClient.post(httpRequest)

        if (!response.status.isSuccess()) {
            throw Exception("TTS request failed: ${response.status.value} ${response.status.description}")
        }

        val audioData = response.bodyAsBytes()

        emit(
            AudioChunk(
                data = audioData,
                format = AudioFormat.MP3,
                isLast = true,
                metadata = mapOf(
                    "provider" to "openai",
                    "model" to providerSetting.model,
                    "voice" to providerSetting.voice
                )
            )
        )
    }
}
