package me.rerere.tts.provider.providers

import android.content.Context
import me.rerere.common.logging.RikkaLog as Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import me.rerere.tts.model.AudioChunk
import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.provider.TTSProvider
import me.rerere.tts.provider.TTSProviderSetting
import org.json.JSONObject

private const val TAG = "GroqTTSProvider"

class GroqTTSProvider : TTSProvider<TTSProviderSetting.Groq> {
    override fun generateSpeech(
        context: Context,
        providerSetting: TTSProviderSetting.Groq,
        request: TTSRequest
    ): Flow<AudioChunk> = flow {
        val requestBody = JSONObject().apply {
            put("model", providerSetting.model)
            put("input", request.text)
            put("voice", providerSetting.voice)
            put("response_format", "wav")
        }

        Log.i(TAG, "generateSpeech: $requestBody")

        val response = postRemoteTtsRequest(
            url = "${providerSetting.baseUrl}/audio/speech",
            body = requestBody.toString(),
            headers = mapOf(
                "Authorization" to "Bearer ${providerSetting.apiKey}",
                "Content-Type" to "application/json",
            ),
        )

        if (!response.isSuccessful) {
            Log.e(TAG, "generateSpeech: ${response.code} ${response.message}")
            Log.e(TAG, "generateSpeech: ${response.bodyText()}")
            throw Exception("Groq TTS request failed: ${response.code} ${response.message}")
        }

        val audioData = response.bodyBytes()

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
