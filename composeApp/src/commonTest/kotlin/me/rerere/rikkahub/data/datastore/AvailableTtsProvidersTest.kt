package me.rerere.rikkahub.data.datastore

import me.rerere.tts.provider.TTSProviderSetting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AvailableTtsProvidersTest {
    private val system = TTSProviderSetting.SystemTTS()
    private val first = TTSProviderSetting.OpenAI()
    private val second = TTSProviderSetting.Gemini()
    private val settings = Settings(ttsProviders = listOf(system, first, second), selectedTTSProviderId = system.id)

    @Test
    fun linuxHidesSavedSystemProvidersAndFallsBackWithoutChangingStorage() {
        assertEquals(listOf(first, second), settings.getAvailableTTSProviders(isLinux = true))
        assertEquals(first, settings.getSelectedAvailableTTSProvider(isLinux = true))
        assertEquals(system.id, settings.selectedTTSProviderId)
        assertEquals(listOf(system, first, second), settings.ttsProviders)
        assertEquals(second, settings.copy(selectedTTSProviderId = second.id).getSelectedAvailableTTSProvider(true))
        assertNull(settings.copy(ttsProviders = listOf(system)).getSelectedAvailableTTSProvider(true))
    }

    @Test
    fun supportedPlatformsRetainSystemTtsAndExistingSelectionRules() {
        assertEquals(settings.ttsProviders, settings.getAvailableTTSProviders(isLinux = false))
        assertEquals(system, settings.getSelectedAvailableTTSProvider(isLinux = false))
        assertEquals(second, settings.copy(selectedTTSProviderId = second.id).getSelectedAvailableTTSProvider(false))
        assertIs<TTSProviderSetting.SystemTTS>(newTtsProvider(isLinux = false))
        assertTrue(TTSProviderSetting.SystemTTS::class in availableTtsProviderTypes(isLinux = false))
    }

    @Test
    fun linuxCannotAddSystemTtsThroughDefaultOrTypeSelector() {
        assertIs<TTSProviderSetting.OpenAI>(newTtsProvider(isLinux = true))
        assertFalse(TTSProviderSetting.SystemTTS::class in availableTtsProviderTypes(isLinux = true))
        assertEquals(TTSProviderSetting.Types.size - 1, availableTtsProviderTypes(isLinux = true).size)
    }

    @Test
    fun reorderingVisibleProvidersKeepsHiddenSystemProviders() {
        val reordered = settings.copy(ttsProviders = settings.moveTtsProvider(second.id, first.id))
        assertEquals(listOf(second, first), reordered.getAvailableTTSProviders(isLinux = true))
        assertEquals(listOf(system, second, first), reordered.ttsProviders)
        assertEquals(settings.ttsProviders, reordered.moveTtsProvider(second.id, first.id))
    }
}
