package me.rerere.rikkahub.web

import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class WebServerAvailabilityTest {
    @Test
    fun keepsTheExistingUnavailableHostError() = runTest {
        val runtime = createIosWebServerRuntime(this)
        runtime.start(43123, localhostOnly = true)
        advanceUntilIdle()
        assertFalse(runtime.state.value.isRunning)
        assertFalse(runtime.state.value.isLoading)
        assertEquals("Web server hosting is unavailable on iOS", runtime.state.value.error)
    }
}
