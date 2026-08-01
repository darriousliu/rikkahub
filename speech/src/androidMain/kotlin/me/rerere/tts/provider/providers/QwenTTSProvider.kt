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
import io.ktor.utils.io.readLine
import org.json.JSONObject

private const val TAG = "QwenTTSProvider"

class QwenTTSProvider : TTSProvider<TTSProviderSetting.Qwen> {
    override fun generateSpeech(
        context: Context,
        providerSetting: TTSProviderSetting.Qwen,
        request: TTSRequest
    ): Flow<AudioChunk> = flow {
        val requestBody = JSONObject().apply {
            put("model", providerSetting.model)
            put("input", JSONObject().apply {
                put("text", request.text)
                put("voice", providerSetting.voice)
                put("language_type", providerSetting.languageType)
            })
        }

        Log.i(TAG, "generateSpeech: $requestBody")

        val response = postRemoteTtsRequest(
            url = "${providerSetting.baseUrl}/services/aigc/multimodal-generation/generation",
            body = requestBody.toString(),
            headers = mapOf(
                "Authorization" to "Bearer ${providerSetting.apiKey}",
                "Content-Type" to "application/json",
                "X-DashScope-SSE" to "enable",
            ),
        )

        if (!response.isSuccessful) {
            val errorBody = response.bodyText()
            Log.e(TAG, "Qwen TTS request failed: ${response.code} ${response.message}, body: $errorBody")
            throw Exception("Qwen TTS request failed: ${response.code} ${response.message}")
        }

        val channel = response.bodyChannel()
        var currentData = StringBuilder()
        while (!channel.isClosedForRead) {
            val line = channel.readLine() ?: break
            when {
                line.startsWith("data:") -> {
                    currentData.append(line.removePrefix("data:"))
                }

                line.isEmpty() && currentData.isNotEmpty() -> {
                    val result = parseSSEData(currentData.toString())
                    if (result != null) {
                        val (audioData, isLast) = result
                        emit(
                            AudioChunk(
                                data = audioData,
                                format = AudioFormat.PCM,
                                sampleRate = 24000,
                                isLast = isLast,
                                metadata = mapOf(
                                    "provider" to "qwen",
                                    "model" to providerSetting.model,
                                    "voice" to providerSetting.voice,
                                    "sampleRate" to "24000",
                                    "channels" to "1",
                                    "bitDepth" to "16"
                                )
                            )
                        )
                    }
                    currentData = StringBuilder()
                }
            }
        }
    }

    private fun parseSSEData(data: String): Pair<ByteArray, Boolean>? {
        return try {
            val json = JSONObject(data)
            val output = json.optJSONObject("output") ?: return null
            val audio = output.optJSONObject("audio") ?: return null
            val audioBase64 = audio.optString("data", "")
            val finishReason = output.optString("finish_reason", "")

            if (audioBase64.isNotEmpty()) {
                val audioData = decodeAudioBase64(audioBase64)
                val isLast = finishReason == "stop"
                Pair(audioData, isLast)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse SSE data: $data", e)
            null
        }
    }
}
