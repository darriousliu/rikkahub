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

private const val TAG = "ElevenLabsTTSProvider"

class ElevenLabsTTSProvider(
    private val httpClient: HttpClient,
) : TTSProvider<TTSProviderSetting.ElevenLabs> {
    override fun generateSpeech(
        providerSetting: TTSProviderSetting.ElevenLabs,
        request: TTSRequest
    ): Flow<AudioChunk> = flow {
        val requestBody = buildJsonObject {
            put("text", request.text)
            put("model_id", providerSetting.model)
            put("voice_settings", buildJsonObject {
                put("stability", providerSetting.stability.toDouble())
                put("similarity_boost", providerSetting.similarityBoost.toDouble())
            })
        }

        Log.i(TAG, "generateSpeech: model=${providerSetting.model}, voiceId=${providerSetting.voiceId}")

        val response = httpClient.postRemoteTtsRequest(
            url = "${providerSetting.baseUrl}/v1/text-to-speech/${providerSetting.voiceId}?output_format=mp3_44100_128",
            body = requestBody.toString(),
            headers = mapOf(
                "xi-api-key" to providerSetting.apiKey,
                "Content-Type" to "application/json",
            ),
        )

        if (!response.isSuccessful) {
            val errorBody = response.bodyText()
            Log.e(TAG, "generateSpeech: ${response.code} ${response.message}")
            Log.e(TAG, "generateSpeech: $errorBody")
            throw Exception("ElevenLabs TTS request failed: ${response.code} ${response.message}")
        }

        val audioData = response.bodyBytes()

        emit(
            AudioChunk(
                data = audioData,
                format = AudioFormat.MP3,
                isLast = true,
                metadata = mapOf(
                    "provider" to "elevenlabs",
                    "model" to providerSetting.model,
                    "voiceId" to providerSetting.voiceId
                )
            )
        )
    }
}
