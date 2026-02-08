package me.rerere.tts.provider.providers

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.cancel
import io.ktor.utils.io.readLine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.common.PlatformContext
import me.rerere.tts.model.AudioChunk
import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.provider.TTSProvider
import me.rerere.tts.provider.TTSProviderSetting
import kotlin.io.encoding.Base64


private const val TAG = "QwenTTSProvider"

class QwenTTSProvider : TTSProvider<TTSProviderSetting.Qwen> {
    private val httpClient = HttpClient {
        install(HttpTimeout) {
            requestTimeoutMillis = 120_000
        }
    }

    @Serializable
    private data class SSEResponse(
        val output: SSEOutput? = null
    )

    @Serializable
    private data class SSEOutput(
        val audio: SSEAudio? = null,
        @SerialName("finish_reason")
        val finishReason: String? = null
    )

    @Serializable
    private data class SSEAudio(
        val data: String? = null
    )

    override fun generateSpeech(
        context: PlatformContext,
        providerSetting: TTSProviderSetting.Qwen,
        request: TTSRequest
    ): Flow<AudioChunk> = flow {
        val requestBody = buildJsonObject {
            put("model", providerSetting.model)
            put("input", buildJsonObject {
                put("text", request.text)
                put("voice", providerSetting.voice)
                put("language_type", providerSetting.languageType)
            })
        }

        Logger.i(TAG) { "generateSpeech: $requestBody" }

        val httpRequest = HttpRequestBuilder().apply {
            url("${providerSetting.baseUrl}/services/aigc/multimodal-generation/generation")
            header("Authorization", "Bearer ${providerSetting.apiKey}")
            header("Content-Type", "application/json")
            header("X-DashScope-SSE", "enable")
            method = HttpMethod.Post
            contentType(ContentType.Application.Json)
            setBody(requestBody.toString())
        }


        val response = httpClient.request(httpRequest)

        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            Logger.e(TAG) { "Qwen TTS request failed: ${response.status.value} ${response.status.description}, body: $errorBody" }
            throw Exception("Qwen TTS request failed: ${response.status.value} ${response.status.description}")
        }

        val channel = response.bodyAsChannel()
        var currentData = StringBuilder()

        try {
            while (!channel.isClosedForRead) {
                // 逐行读取，readUTF8Line 在通道关闭时返回 null
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
        } finally {
            channel.cancel()
        }
    }

    private fun parseSSEData(data: String): Pair<ByteArray, Boolean>? {
        return try {
            val json = Json.decodeFromString<SSEResponse>(data)
            val output = json.output ?: return null
            val audio = output.audio ?: return null
            val audioBase64 = audio.data.orEmpty()

            if (audioBase64.isNotEmpty()) {
                val audioData = Base64.decode(audioBase64)
                val isLast = output.finishReason == "stop"
                Pair(audioData, isLast)
            } else {
                null
            }
        } catch (e: Exception) {
            Logger.e(TAG, e) { "Failed to parse SSE data: $data" }
            null
        }
    }
}
