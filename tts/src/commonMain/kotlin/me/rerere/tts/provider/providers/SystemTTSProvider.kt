package me.rerere.tts.provider.providers

import me.rerere.common.PlatformContext
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.model.TTSResponse
import me.rerere.tts.provider.TTSProvider
import me.rerere.tts.provider.TTSProviderSetting

expect class SystemTTSProvider(): TTSProvider<TTSProviderSetting.SystemTTS> {
    override suspend fun generateSpeech(
        context: PlatformContext,
        providerSetting: TTSProviderSetting.SystemTTS,
        request: TTSRequest
    ): TTSResponse
}
