package me.rerere.tts.provider

import me.rerere.common.PlatformContext
import kotlinx.coroutines.flow.Flow
import me.rerere.tts.model.AudioChunk
import me.rerere.tts.model.TTSRequest

interface TTSProvider<T : TTSProviderSetting> {
    fun generateSpeech(
        context: PlatformContext,
        providerSetting: T,
        request: TTSRequest
    ): Flow<AudioChunk>
}
