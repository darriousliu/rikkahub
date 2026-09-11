package me.rerere.rikkahub.web

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import me.rerere.rikkahub.platform.RegisteredServiceInfo
import me.rerere.rikkahub.platform.ServiceRegistrar
import me.rerere.rikkahub.platform.ServiceRegistration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebServerManagerTest {
    @Test
    fun startsOnLoopbackAndKeepsOriginalLoadingStopAndDuplicateCallBehavior() = runTest {
        val host = Host()
        val registrar = Registrar()
        val manager = WebServerManager(this, host, registrar)

        manager.start(43123, localhostOnly = true)
        assertEquals(WebServerState(), manager.state.value)
        advanceUntilIdle()
        assertEquals(WebServerState(isRunning = true, port = 43123, localhostOnly = true), manager.state.value)
        assertEquals(listOf("available:43123", "start:127.0.0.1:43123"), host.calls)
        assertEquals(0, registrar.registrations.size)

        manager.start(43124, localhostOnly = false)
        advanceUntilIdle()
        assertEquals(2, host.calls.size)
        manager.stop()
        assertFalse(manager.state.value.isRunning)
        assertTrue(manager.state.value.isLoading)
        advanceUntilIdle()
        manager.stop()
        advanceUntilIdle()
        assertEquals(listOf("available:43123", "start:127.0.0.1:43123", "stop:1000:2000"), host.calls)
        assertEquals(2, registrar.unregisterCount)
        assertEquals(WebServerState(port = 43123, localhostOnly = true), manager.state.value)
    }

    @Test
    fun portAvailabilityKeepsTheOriginalErrorAndDoesNotAddConfigValidation() = runTest {
        val host = Host().apply { available = false }
        val manager = WebServerManager(this, host)
        for (port in listOf(43123, -1, 65536)) {
            manager.start(port, serviceName = "", localhostOnly = true)
            advanceUntilIdle()
            assertEquals(
                WebServerState(
                    port = port,
                    serviceName = "",
                    localhostOnly = true,
                    error = "Port $port is already in use",
                ),
                manager.state.value,
            )
        }
        assertEquals(listOf("available:43123", "available:-1", "available:65536"), host.calls)
        host.available = true
        manager.start(0, serviceName = "", localhostOnly = true)
        advanceUntilIdle()
        assertEquals(WebServerState(isRunning = true, port = 0, serviceName = "", localhostOnly = true),
            manager.state.value)
        manager.stop()
        advanceUntilIdle()
    }

    @Test
    fun startExceptionsIncludingCancellationKeepTheirOriginalMessages() = runTest {
        val host = Host()
        val manager = WebServerManager(this, host)
        for (failure in listOf(IllegalStateException("bind failed"), CancellationException("cancelled"), Exception())) {
            host.startFailure = failure
            manager.start(43123, localhostOnly = true)
            advanceUntilIdle()
            assertEquals(WebServerState(port = 43123, localhostOnly = true, error = failure.message),
                manager.state.value)
        }
        host.startFailure = null
        manager.start(43123, localhostOnly = true)
        advanceUntilIdle()
        assertTrue(manager.state.value.isRunning)
        manager.stop()
        advanceUntilIdle()
    }

    @Test
    fun registersOnlyAfterStartingAndKeepsServiceDiscoveryFailuresNonFatal() = runTest {
        val host = Host()
        val registrar = Registrar()
        val manager = WebServerManager(this, host, registrar)
        registrar.onRegister = { assertTrue(manager.state.value.isRunning) }
        manager.start(43123, serviceName = "test-server", localhostOnly = false)
        advanceUntilIdle()
        assertEquals("start:0.0.0.0:43123", host.calls.last())
        assertEquals(listOf(ServiceRegistration(43123, "test-server")), registrar.registrations)
        assertEquals(WebServerState(isRunning = true, port = 43123, serviceName = "registered-name",
            hostname = "test.local", address = "192.0.2.1"), manager.state.value)
        manager.stop()
        assertEquals(null, manager.state.value.hostname)
        assertEquals(null, manager.state.value.address)
        advanceUntilIdle()

        registrar.failure = IllegalStateException("discovery unavailable")
        manager.start(43123, localhostOnly = false)
        advanceUntilIdle()
        assertEquals(WebServerState(isRunning = true, port = 43123), manager.state.value)
        manager.stop()
        advanceUntilIdle()
        assertEquals(WebServerState(port = 43123), manager.state.value)
    }

    @Test
    fun reportErrorAndFailedStopDoNotDiscardTheActiveServer() = runTest {
        val host = Host()
        val manager = WebServerManager(this, host)
        manager.start(43123, localhostOnly = true)
        advanceUntilIdle()
        manager.reportError("foreground service failed")
        manager.start(43124, localhostOnly = true)
        advanceUntilIdle()
        assertEquals(2, host.calls.size)
        assertEquals("foreground service failed", manager.state.value.error)

        host.stopFailure = IllegalStateException("stop failed")
        manager.stop()
        advanceUntilIdle()
        assertEquals("stop failed", manager.state.value.error)
        assertFalse(manager.state.value.isLoading)
        manager.start(43124, localhostOnly = true)
        advanceUntilIdle()
        assertEquals(3, host.calls.size)
        host.stopFailure = null
        manager.stop()
        advanceUntilIdle()
        manager.start(43124, localhostOnly = true)
        advanceUntilIdle()
        assertEquals(WebServerState(isRunning = true, port = 43124, localhostOnly = true), manager.state.value)
        manager.stop()
        advanceUntilIdle()
    }

    @Test
    fun queuedStartsAndRestartRetainTheTagSchedulingWithoutANewStateMachine() = runTest {
        val host = Host()
        val manager = WebServerManager(this, host)
        manager.start(43123, localhostOnly = true)
        manager.start(43124, localhostOnly = true)
        advanceUntilIdle()
        assertEquals(listOf("available:43123", "start:127.0.0.1:43123",
            "available:43124", "start:127.0.0.1:43124"), host.calls)

        // 2.4.5 queues stop, then start sees the existing server and returns. There is no product restart caller.
        manager.restart()
        advanceUntilIdle()
        assertFalse(manager.state.value.isRunning)
        assertEquals("stop:1000:2000", host.calls.last())
        assertEquals(5, host.calls.size)
        // The actual UI waits for stop to finish before allowing start again.
        manager.start(43125, localhostOnly = true)
        advanceUntilIdle()
        assertEquals(43125, manager.state.value.port)
        assertTrue(manager.state.value.isRunning)
        manager.stop()
        advanceUntilIdle()
    }

    private class Host : WebServerHost {
        val calls = mutableListOf<String>()
        var available = true
        var startFailure: Exception? = null
        var stopFailure: Exception? = null
        override fun isPortAvailable(port: Int): Boolean {
            calls += "available:$port"
            return available
        }
        override suspend fun start(port: Int, host: String) {
            calls += "start:$host:$port"
            startFailure?.let { throw it }
        }
        override suspend fun stop(gracePeriodMillis: Long, timeoutMillis: Long) {
            calls += "stop:$gracePeriodMillis:$timeoutMillis"
            stopFailure?.let { throw it }
        }
    }

    private class Registrar : ServiceRegistrar {
        val registrations = mutableListOf<ServiceRegistration>()
        var unregisterCount = 0
        var failure: Exception? = null
        var onRegister: () -> Unit = {}
        override suspend fun register(registration: ServiceRegistration): Result<RegisteredServiceInfo> {
            registrations += registration
            onRegister()
            return failure?.let { Result.failure(it) } ?: Result.success(
                RegisteredServiceInfo("registered-name", "test.local", registration.port, "192.0.2.1"),
            )
        }
        override suspend fun unregister(): Result<Unit> {
            unregisterCount += 1
            return failure?.let { Result.failure(it) } ?: Result.success(Unit)
        }
    }
}
