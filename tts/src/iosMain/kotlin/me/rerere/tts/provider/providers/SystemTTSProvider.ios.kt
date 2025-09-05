package me.rerere.tts.provider.providers

import me.rerere.tts.model.TTSRequest
import me.rerere.tts.model.TTSResponse
import me.rerere.tts.provider.TTSProvider
import me.rerere.tts.provider.TTSProviderSetting

actual class SystemTTSProvider : TTSProvider<TTSProviderSetting.SystemTTS> {
    actual override suspend fun generateSpeech(
        context: me.rerere.common.PlatformContext,
        providerSetting: TTSProviderSetting.SystemTTS,
        request: TTSRequest
    ): TTSResponse {
        TODO("Not yet implemented")
    }
}
