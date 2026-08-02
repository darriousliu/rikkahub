package me.rerere.rikkahub.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

class WebServerControllerTest {
    @Test
    fun exposesRunningEndpointAndStopsIdempotently() = runTest {
        val host = RecordingHost()
        val controller = WebServerController(host)
        val config = WebServerConfig(port = 0, localhostOnly = true)

        val running = assertIs<WebServerState.Running>(controller.start(config))
        assertEquals(WebServerEndpoint("127.0.0.1", 43123), running.endpoint)
        assertEquals(running, controller.start(config))
        assertEquals(1, host.startCount)

        assertEquals(WebServerState.Stopped, controller.stop())
        assertEquals(WebServerState.Stopped, controller.stop())
        assertEquals(1, host.stopCount)
    }

    @Test
    fun exposesUnavailableHostWithoutThrowing() = runTest {
        val controller = WebServerController(
            UnavailableHost("not supported")
        )

        val state = assertIs<WebServerState.Unavailable>(controller.start())

        assertEquals("not supported", state.reason)
        assertEquals(state, controller.stop())
    }
}

private class RecordingHost : WebServerHost {
    var startCount: Int = 0
    var stopCount: Int = 0

    override suspend fun start(config: WebServerConfig): WebServerHostStartResult {
        startCount += 1
        return WebServerHostStartResult.Running(WebServerEndpoint("127.0.0.1", 43123))
    }

    override suspend fun stop() {
        stopCount += 1
    }
}

private class UnavailableHost(
    private val reason: String,
) : WebServerHost {
    override suspend fun start(config: WebServerConfig): WebServerHostStartResult =
        WebServerHostStartResult.Unavailable(reason)

    override suspend fun stop() = Unit
}
