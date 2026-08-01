package me.rerere.tts.provider.providers

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import me.rerere.tts.model.AudioChunk
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.provider.TTSProvider
import me.rerere.tts.provider.TTSProviderSetting

class JvmSystemTTSProvider : TTSProvider<TTSProviderSetting.SystemTTS> {
    val isAvailable: Boolean = false

    override fun generateSpeech(
        providerSetting: TTSProviderSetting.SystemTTS,
        request: TTSRequest,
    ): Flow<AudioChunk> = flow {
        throw UnsupportedOperationException(UNAVAILABLE_MESSAGE)
    }

    companion object {
        const val UNAVAILABLE_MESSAGE = "System TTS is unavailable on JVM"
    }
}
