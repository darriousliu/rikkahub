package me.rerere.asr.providers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import me.rerere.common.logging.RikkaLog as Log
import me.rerere.asr.ASRProviderSetting
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSource
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import kotlin.io.encoding.Base64

private const val MAX_STEP_RETRY = 3
private const val TAG = "BatchAsrHttp"
private val JSON_MEDIA_TYPE = "application/json".toMediaType()

internal suspend fun transcribeMiMoAudio(
    httpClient: OkHttpClient,
    provider: ASRProviderSetting.MiMo,
    wavBytes: ByteArray,
): String {
    val b64 = Base64.Default.encode(wavBytes)
    val message = JSONObject()
        .put("role", "user")
        .put(
            "content",
            JSONArray().put(
                JSONObject()
                    .put("type", "input_audio")
                    .put(
                        "input_audio",
                        JSONObject().put("data", "data:audio/wav;base64,$b64")
                    )
            )
        )

    val body = JSONObject()
        .put("model", provider.model)
        .put("messages", JSONArray().put(message))
    if (provider.language.isNotBlank()) {
        body.put("asr_options", JSONObject().put("language", provider.language))
    }

    val request = Request.Builder()
        .url("${provider.baseUrl.trimEnd('/')}/chat/completions")
        .addHeader("api-key", provider.apiKey)
        .addHeader("Content-Type", "application/json")
        .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
        .build()

    return withContext(Dispatchers.IO) {
        httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body.string()
            if (!response.isSuccessful) {
                throw IOException("MiMo ASR HTTP ${response.code}: $responseBody")
            }
            val json = runCatching { JSONObject(responseBody) }.getOrElse {
                throw IOException("MiMo ASR response is not valid JSON: $responseBody")
            }
            json.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content", "")
                ?.trim()
                ?: ""
        }
    }
}

internal suspend fun transcribeStepAudio(
    httpClient: OkHttpClient,
    provider: ASRProviderSetting.Step,
    pcmBytes: ByteArray,
): String {
    val transcription = JSONObject()
        .put("model", provider.model)
        .put("enable_itn", provider.enableItn)
        .put("enable_timestamp", provider.enableTimestamp)
    if (provider.language.isNotBlank()) {
        transcription.put("language", provider.language)
    }
    if (provider.hotwords.isNotEmpty()) {
        transcription.put("hotwords", JSONArray(provider.hotwords))
    }

    val body = JSONObject()
        .put(
            "audio",
            JSONObject()
                .put("data", Base64.Default.encode(pcmBytes))
                .put(
                    "input",
                    JSONObject()
                        .put("transcription", transcription)
                        .put(
                            "format",
                            JSONObject()
                                .put("type", "pcm")
                                .put("codec", "pcm_s16le")
                                .put("rate", provider.sampleRate)
                                .put("bits", 16)
                                .put("channel", 1)
                        )
                )
        )

    val request = Request.Builder()
        .url("${provider.baseUrl.trimEnd('/')}/v1/audio/asr/sse")
        .addHeader("Authorization", "Bearer ${provider.apiKey}")
        .addHeader("Accept", "text/event-stream")
        .addHeader("Content-Type", "application/json")
        .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
        .build()

    var lastError: IOException? = null
    for (attempt in 1..MAX_STEP_RETRY) {
        try {
            return withContext(Dispatchers.IO) {
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Step ASR HTTP ${response.code}: ${response.body.string()}")
                    }
                    parseStepSseTranscript(response.body.source())
                }
            }
        } catch (error: IOException) {
            lastError = error
            Log.w(TAG, "transcribeStepAudio attempt $attempt/$MAX_STEP_RETRY failed: ${error.message}")
            if (attempt < MAX_STEP_RETRY) {
                delay(300L * attempt)
            }
        }
    }
    throw lastError ?: IOException("Step ASR request failed")
}

private fun parseStepSseTranscript(source: BufferedSource): String {
    val transcript = StringBuilder()
    var eventType: String? = null
    val dataLines = mutableListOf<String>()

    fun dispatchEvent(): Boolean {
        if (eventType == null && dataLines.isEmpty()) return false
        val data = dataLines.joinToString("\n")
        val shouldStop = handleStepSseEvent(eventType, data, transcript)
        eventType = null
        dataLines.clear()
        return shouldStop
    }

    while (true) {
        val line = source.readUtf8Line() ?: break
        if (line.isEmpty()) {
            if (dispatchEvent()) break
            continue
        }
        if (line.startsWith(":")) continue

        val separatorIndex = line.indexOf(':')
        val field = if (separatorIndex == -1) line else line.substring(0, separatorIndex)
        val value = if (separatorIndex == -1) "" else line.substring(separatorIndex + 1).removePrefix(" ")
        when (field) {
            "event" -> eventType = value
            "data" -> dataLines.add(value)
        }
    }
    dispatchEvent()
    return transcript.toString().trim()
}

private fun handleStepSseEvent(
    eventType: String?,
    data: String,
    transcript: StringBuilder,
): Boolean {
    if (data == "[DONE]") return true

    val json = runCatching { JSONObject(data) }.getOrNull()
    val type = eventType?.takeIf { it.isNotBlank() }
        ?: json?.optString("type")?.takeIf { it.isNotBlank() }

    return when (type) {
        "transcript.text.delta" -> {
            transcript.append(extractStepTranscriptText(json, if (json == null) data else ""))
            false
        }

        "transcript.text.done" -> {
            val finalText = extractStepTranscriptText(json, "")
            if (finalText.isNotBlank()) {
                transcript.clear()
                transcript.append(finalText)
            }
            true
        }

        "error" -> throw IOException("Step ASR error: ${extractStepErrorMessage(json, data)}")
        else -> {
            val text = extractStepTranscriptText(json, "")
            if (text.isNotBlank()) transcript.append(text)
            false
        }
    }
}

private fun extractStepTranscriptText(json: JSONObject?, fallback: String): String {
    if (json == null) return fallback
    for (key in listOf("delta", "text", "content", "transcript")) {
        val value = json.opt(key) ?: continue
        if (value is JSONObject) {
            val nestedValue = extractStepTranscriptText(value, "")
            if (nestedValue.isNotBlank()) return nestedValue
        } else {
            val text = value.toString()
            if (text.isNotBlank()) return text
        }
    }

    for (key in listOf("data", "result", "transcript")) {
        val nested = json.optJSONObject(key) ?: continue
        val value = extractStepTranscriptText(nested, "")
        if (value.isNotBlank()) return value
    }
    return fallback
}

private fun extractStepErrorMessage(json: JSONObject?, fallback: String): String {
    if (json == null) return fallback
    val error = json.optJSONObject("error")
    if (error != null) {
        val message = error.optString("message", "")
        if (message.isNotBlank()) return message
    }
    return json.optString("message", fallback)
}
