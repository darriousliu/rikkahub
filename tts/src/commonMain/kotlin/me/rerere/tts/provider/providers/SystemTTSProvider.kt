package me.rerere.tts.provider.providers

import kotlinx.coroutines.flow.Flow
import me.rerere.common.PlatformContext
import me.rerere.tts.model.AudioChunk
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.provider.TTSProvider
import me.rerere.tts.provider.TTSProviderSetting



expect class SystemTTSProvider() : TTSProvider<TTSProviderSetting.SystemTTS> {
    override fun generateSpeech(
        context: PlatformContext,
        providerSetting: TTSProviderSetting.SystemTTS,
        request: TTSRequest
    ): Flow<AudioChunk>
}
