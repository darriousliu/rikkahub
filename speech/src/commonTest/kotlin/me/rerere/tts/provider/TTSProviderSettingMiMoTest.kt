package me.rerere.tts.provider

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TTSProviderSettingMiMoTest {
    @Test
    fun mimo_defaults_are_expected() {
        val setting = TTSProviderSetting.MiMo()

        assertEquals("MiMo TTS", setting.name)
        assertEquals("https://api.xiaomimimo.com/v1", setting.baseUrl)
        assertEquals("mimo-v2.5-tts", setting.model)
        assertEquals("mimo_default", setting.voice)
        assertEquals("", setting.apiKey)
    }

    @Test
    fun mimo_is_registered_in_provider_types() {
        assertTrue(TTSProviderSetting.Types.contains(TTSProviderSetting.MiMo::class))
    }
}
