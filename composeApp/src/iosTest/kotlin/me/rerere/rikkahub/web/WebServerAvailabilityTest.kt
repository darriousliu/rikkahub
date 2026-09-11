package me.rerere.rikkahub.web

import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import me.rerere.rikkahub.shared.CapabilityState
import me.rerere.rikkahub.shared.PlatformCapability
import me.rerere.rikkahub.shared.PlatformKind
import me.rerere.rikkahub.shared.capabilityState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class WebServerAvailabilityTest {
    @Test
    fun keepsTheExistingUnavailablePlatformCapabilityAndError() = runTest {
        assertEquals(CapabilityState.UNAVAILABLE, capabilityState(PlatformKind.IOS, PlatformCapability.WEB_SERVER))
        val runtime = createIosWebServerRuntime(this)
        runtime.start(43123, localhostOnly = true)
        advanceUntilIdle()
        assertFalse(runtime.state.value.isRunning)
        assertFalse(runtime.state.value.isLoading)
        assertEquals("Web server hosting is unavailable on iOS", runtime.state.value.error)
    }
}
