package me.rerere.tts.provider.providers

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import me.rerere.tts.model.AudioChunk
import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.provider.TTSProvider
import me.rerere.tts.provider.TTSProviderSetting

class JvmSystemTTSProvider internal constructor(
    private val osName: String,
) : TTSProvider<TTSProviderSetting.SystemTTS> {
    constructor() : this(System.getProperty("os.name"))

    val isAvailable: Boolean = supportsSystemTts(osName)

    override fun generateSpeech(
        providerSetting: TTSProviderSetting.SystemTTS,
        request: TTSRequest,
    ): Flow<AudioChunk> = flow {
        val audioData = when {
            osName.startsWith("Mac", ignoreCase = true) ->
                MacSystemSpeechSynthesizer.synthesize(providerSetting, request.text)
            osName.startsWith("Windows", ignoreCase = true) ->
                WindowsSystemSpeechSynthesizer().synthesize(providerSetting, request.text)
            else -> throw UnsupportedOperationException(UNAVAILABLE_MESSAGE)
        }
        emit(
            AudioChunk(
                data = audioData,
                format = AudioFormat.WAV,
                isLast = true,
                metadata = mapOf(
                    "provider" to "system",
                    "speechRate" to providerSetting.speechRate.toString(),
                    "pitch" to providerSetting.pitch.toString(),
                ),
            )
        )
    }

    companion object {
        const val UNAVAILABLE_MESSAGE = "System TTS is only available on Windows and macOS desktops"

        internal fun supportsSystemTts(osName: String): Boolean =
            osName.startsWith("Mac", ignoreCase = true) || osName.startsWith("Windows", ignoreCase = true)
    }
}
