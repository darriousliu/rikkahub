package me.rerere.tts.provider

import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TTSProviderSettingElevenLabsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testElevenLabsSerialization() {
        val setting = TTSProviderSetting.ElevenLabs(
            id = Uuid.parse("00000000-0000-0000-0000-000000000001"),
            name = "Test ElevenLabs",
            apiKey = "test-key",
            baseUrl = "https://api.elevenlabs.io",
            model = "eleven_v3",
            voiceId = "test-voice-id",
            stability = 0.5f,
            similarityBoost = 0.75f,
        )

        val encoded = json.encodeToString(TTSProviderSetting.serializer(), setting)
        assertTrue(encoded.contains("\"elevenlabs\""))
        assertTrue(encoded.contains("\"test-key\""))
        assertTrue(encoded.contains("\"eleven_v3\""))

        val decoded = json.decodeFromString(
            TTSProviderSetting.serializer(),
            encoded
        ) as TTSProviderSetting.ElevenLabs

        assertEquals(setting.id, decoded.id)
        assertEquals(setting.apiKey, decoded.apiKey)
        assertEquals(setting.model, decoded.model)
        assertEquals(setting.voiceId, decoded.voiceId)
        assertEquals(setting.stability, decoded.stability, 0.01f)
        assertEquals(setting.similarityBoost, decoded.similarityBoost, 0.01f)
    }
}
