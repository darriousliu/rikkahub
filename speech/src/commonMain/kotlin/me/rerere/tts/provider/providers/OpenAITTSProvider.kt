package me.rerere.tts.provider.providers

import io.ktor.client.HttpClient
import me.rerere.common.logging.RikkaLog as Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.tts.model.AudioChunk
import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.provider.TTSProvider
import me.rerere.tts.provider.TTSProviderSetting

private const val TAG = "OpenAITTSProvider"

class OpenAITTSProvider(
    private val httpClient: HttpClient,
) : TTSProvider<TTSProviderSetting.OpenAI> {
    override fun generateSpeech(
        providerSetting: TTSProviderSetting.OpenAI,
        request: TTSRequest
    ): Flow<AudioChunk> = flow {
        val requestBody = buildJsonObject {
            put("model", providerSetting.model)
            put("input", request.text)
            put("voice", providerSetting.voice)
            put("response_format", "mp3") // Default to MP3
        }

        Log.i(TAG, "generateSpeech: $requestBody")

        val response = httpClient.postRemoteTtsRequest(
            url = "${providerSetting.baseUrl}/audio/speech",
            body = requestBody.toString(),
            headers = mapOf(
                "Authorization" to "Bearer ${providerSetting.apiKey}",
                "Content-Type" to "application/json",
            ),
        )

        if (!response.isSuccessful) {
            throw Exception("TTS request failed: ${response.code} ${response.message}")
        }

        val audioData = response.bodyBytes()

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
