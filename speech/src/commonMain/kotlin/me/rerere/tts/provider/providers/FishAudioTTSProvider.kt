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

private const val TAG = "FishAudioTTSProvider"

class FishAudioTTSProvider(
    private val httpClient: HttpClient,
) : TTSProvider<TTSProviderSetting.FishAudio> {
    override fun generateSpeech(
        providerSetting: TTSProviderSetting.FishAudio,
        request: TTSRequest
    ): Flow<AudioChunk> = flow {
        val requestBody = buildJsonObject {
            put("text", request.text)
            if (providerSetting.referenceId.isNotBlank()) {
                put("reference_id", providerSetting.referenceId)
            }
            put("format", providerSetting.format)
            put("temperature", providerSetting.temperature.toDouble())
            put("top_p", providerSetting.topP.toDouble())
            put("prosody", buildJsonObject {
                put("speed", providerSetting.speed.toDouble())
            })
            put("chunk_length", providerSetting.chunkLength)
            put("normalize", providerSetting.normalize)
            put("latency", providerSetting.latency)
        }

        Log.i(TAG, "generateSpeech: model=${providerSetting.model}, referenceId=${providerSetting.referenceId}")

        val response = httpClient.postRemoteTtsRequest(
            url = "${providerSetting.baseUrl}/v1/tts",
            body = requestBody.toString(),
            headers = mapOf(
                "Authorization" to "Bearer ${providerSetting.apiKey}",
                "Content-Type" to "application/json",
                "model" to providerSetting.model,
            ),
        )

        if (!response.isSuccessful) {
            val errorBody = response.bodyText()
            Log.e(TAG, "generateSpeech: ${response.code} ${response.message}")
            Log.e(TAG, "generateSpeech: $errorBody")
            throw Exception("Fish Audio TTS request failed: ${response.code} ${response.message}")
        }

        val audioData = response.bodyBytes()

        val audioFormat = when (providerSetting.format.lowercase()) {
            "mp3" -> AudioFormat.MP3
            "wav" -> AudioFormat.WAV
            "pcm" -> AudioFormat.PCM
            "opus" -> AudioFormat.OPUS
            else -> AudioFormat.MP3
        }

        emit(
            AudioChunk(
                data = audioData,
                format = audioFormat,
                isLast = true,
                metadata = mapOf(
                    "provider" to "fish-audio",
                    "model" to providerSetting.model,
                    "referenceId" to providerSetting.referenceId
                )
            )
        )
    }
}
