package me.rerere.asr.providers

import io.ktor.client.HttpClient
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readLine
import kotlinx.coroutines.delay
import me.rerere.common.logging.RikkaLog as Log
import me.rerere.asr.ASRProviderSetting
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import kotlin.io.encoding.Base64

private const val MAX_STEP_RETRY = 3
private const val TAG = "BatchAsrHttp"

internal suspend fun transcribeMiMoAudio(
    httpClient: HttpClient,
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

    val response = httpClient.postBatchAsrRequest(
        url = "${provider.baseUrl.trimEnd('/')}/chat/completions",
        body = body.toString(),
        headers = mapOf("api-key" to provider.apiKey),
    )
    val responseBody = response.bodyAsText()
    if (!response.status.isSuccess()) {
        throw IOException("MiMo ASR HTTP ${response.status.value}: $responseBody")
    }
    val json = runCatching { JSONObject(responseBody) }.getOrElse {
        throw IOException("MiMo ASR response is not valid JSON: $responseBody")
    }
    return json.optJSONArray("choices")
        ?.optJSONObject(0)
        ?.optJSONObject("message")
        ?.optString("content", "")
        ?.trim()
        ?: ""
}

internal suspend fun transcribeStepAudio(
    httpClient: HttpClient,
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

    var lastError: IOException? = null
    for (attempt in 1..MAX_STEP_RETRY) {
        try {
            val response = httpClient.postBatchAsrRequest(
                url = "${provider.baseUrl.trimEnd('/')}/v1/audio/asr/sse",
                body = body.toString(),
                headers = mapOf(
                    "Authorization" to "Bearer ${provider.apiKey}",
                    "Accept" to "text/event-stream",
                ),
            )
            if (!response.status.isSuccess()) {
                throw IOException("Step ASR HTTP ${response.status.value}: ${response.bodyAsText()}")
            }
            return parseStepSseTranscript(response.bodyAsChannel())
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

private suspend fun parseStepSseTranscript(source: ByteReadChannel): String {
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
        val line = source.readLine() ?: break
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

private suspend fun HttpClient.postBatchAsrRequest(
    url: String,
    body: String,
    headers: Map<String, String>,
): HttpResponse = request(url) {
    method = HttpMethod.Post
    headers.forEach { (name, value) ->
        this.headers.append(name, value)
    }
    this.headers.append("Content-Type", "application/json; charset=utf-8")
    setBody(ByteArrayContent(body.encodeToByteArray(), contentType = null))
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
