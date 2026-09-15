package me.rerere.rikkahub.data.ai.tools.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.event.AppEventBus
import me.rerere.rikkahub.shared.PlatformKind
import me.rerere.rikkahub.shared.currentPlatformKind
import me.rerere.rikkahub.shared.isMacOS
import me.rerere.rikkahub.shared.isWindows
import me.rerere.rikkahub.ui.pages.assistant.detail.platformLocalToolOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DesktopLocalToolsTest {
    @Test
    fun hiddenCalendarToolsAreNotRegisteredEvenWhenPreviouslyEnabled() = runTest {
        val preferences = object : DataStore<Preferences> {
            override val data = MutableStateFlow(emptyPreferences())
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                transform(data.value).also { data.value = it }
        }
        val tools = LocalTools(AppEventBus(), SettingsStore(preferences, backgroundScope), null)
        val originalOs = System.getProperty("os.name")
        // Run each simulated OS in a separate test JVM so static UI options cannot leak between cases.
        val os = System.getProperty("rikkahub.test.osName", originalOs)
        try {
            System.setProperty("os.name", os)
            val windows = os.startsWith("Windows", ignoreCase = true)
            val macOS = os.startsWith("Mac", ignoreCase = true)
            assertEquals(windows, currentPlatformKind.isWindows)
            assertEquals(macOS, currentPlatformKind.isMacOS)
            assertFalse(PlatformKind.ANDROID.isWindows)
            assertFalse(PlatformKind.IOS.isMacOS)
            assertEquals(macOS, LocalToolOption.Calendar in platformLocalToolOptions)
            assertFalse(LocalToolOption.ScreenTime in platformLocalToolOptions)

            val savedOptions = listOf(LocalToolOption.TimeInfo, LocalToolOption.Calendar, LocalToolOption.ScreenTime)
            val expectedNames = if (macOS) {
                listOf("get_time_info", "calendar_query", "calendar_create")
            } else {
                listOf("get_time_info")
            }
            assertEquals(expectedNames, tools.getTools(savedOptions).map { it.name })
        } finally {
            System.setProperty("os.name", originalOs)
        }
    }
}
