package me.rerere.tts.provider.providers

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.sse.sse
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
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

private const val TAG = "MiniMaxTTSProvider"

@Serializable
private data class MiniMaxResponseData(
    val audio: String,
    val status: Int,
    val ced: String
)

@Serializable
private data class MiniMaxResponse(
    val data: MiniMaxResponseData
)

class MiniMaxTTSProvider : TTSProvider<TTSProviderSetting.MiniMax> {
    private val httpClient = HttpClient {
        install(HttpTimeout) {
            socketTimeoutMillis = 60_000
        }
    }


    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override fun generateSpeech(
        context: PlatformContext,
        providerSetting: TTSProviderSetting.MiniMax,
        request: TTSRequest
    ): Flow<AudioChunk> = flow {
        val requestBody = buildJsonObject {
            put("model", providerSetting.model)
            put("text", request.text)
            put("stream", true)
            put("output_format", "hex")
            put("stream_options", buildJsonObject {
                put("exclude_aggregated_audio", true)
            })
            put("voice_setting", buildJsonObject {
                put("voice_id", providerSetting.voiceId)
                put("emotion", providerSetting.emotion)
                put("speed", providerSetting.speed)
            })
        }

        Logger.i(TAG) { "generateSpeech: $requestBody" }

        val httpRequest = HttpRequestBuilder().apply {
            url("${providerSetting.baseUrl}/t2a_v2")
            header("Authorization", "Bearer ${providerSetting.apiKey}")
            header("Content-Type", "application/json")
            method = HttpMethod.Post
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }

        var hasEmittedAudio = false

        try {
            httpClient.sse({ takeFrom(httpRequest) }) {
                Logger.i(TAG) { "SSE connection opened" }
                incoming
                    .onCompletion {
                        Logger.i(TAG) { "SSE connection closed" }
                        // Emit final chunk if we haven't already
                        if (hasEmittedAudio) {
                            emit(
                                AudioChunk(
                                    data = byteArrayOf(), // Empty data for last chunk
                                    format = AudioFormat.MP3,
                                    sampleRate = 32000,
                                    isLast = true,
                                    metadata = mapOf("provider" to "minimax")
                                )
                            )
                        }
                    }
                    .collect {
                        val data = it.data ?: return@collect
                        try {
                            val data = json.decodeFromString<MiniMaxResponse>(data)

                            // Convert hex string to bytes
                            val audioBytes = hexStringToBytes(data.data.audio)

                            emit(
                                AudioChunk(
                                    data = audioBytes,
                                    format = AudioFormat.MP3, // MiniMax returns MP3 format
                                    sampleRate = 32000, // Default sample rate from MiniMax
                                    isLast = false, // Will be set to true on last chunk
                                    metadata = mapOf(
                                        "provider" to "minimax",
                                        "model" to providerSetting.model,
                                        "voice" to providerSetting.voiceId,
                                        "status" to data.data.status.toString(),
                                        "ced" to data.data.ced
                                    )
                                )
                            )
                            hasEmittedAudio = true
                        } catch (e: Exception) {
                            Logger.e(TAG, e) { "Failed to process audio chunk" }
                        }
                    }
            }
        } catch (e: Throwable) {
            Logger.e(TAG, e) { "SSE connection failed" }
            throw e ?: Exception("MiniMax TTS streaming failed")
        }
    }
}

private fun hexStringToBytes(hexString: String): ByteArray {
    val cleanHex = hexString.replace("\\s+".toRegex(), "")
    val length = cleanHex.length

    // Check for even number of characters
    if (length % 2 != 0) {
        throw IllegalArgumentException("Hex string must have even number of characters")
    }

    val bytes = ByteArray(length / 2)
    for (i in 0 until length step 2) {
        val hexByte = cleanHex.substring(i, i + 2)
        bytes[i / 2] = hexByte.toInt(16).toByte()
    }
    return bytes
}
