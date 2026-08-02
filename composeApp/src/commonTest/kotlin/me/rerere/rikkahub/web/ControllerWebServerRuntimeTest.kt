package me.rerere.rikkahub.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import me.rerere.rikkahub.platform.RegisteredServiceInfo
import me.rerere.rikkahub.platform.ServiceRegistrar
import me.rerere.rikkahub.platform.ServiceRegistration

class ControllerWebServerRuntimeTest {
    @Test
    fun mapsHostAndServiceRegistrationIntoUiState() = runTest {
        val host = RuntimeHost()
        val registrar = RuntimeRegistrar()
        val runtime = ControllerWebServerRuntime(
            controller = WebServerController(host),
            scope = this,
            serviceRegistrar = registrar,
        )

        runtime.start(port = 0, localhostOnly = false)
        advanceUntilIdle()

        assertEquals(
            WebServerManagerState(
                isRunning = true,
                port = 43123,
                hostname = "rikkahub.local",
                address = "192.0.2.1",
            ),
            runtime.state.value,
        )
        assertEquals(1, registrar.registerCount)

        runtime.stop()
        advanceUntilIdle()

        assertFalse(runtime.state.value.isRunning)
        assertFalse(runtime.state.value.isLoading)
        assertEquals(1, host.stopCount)
        assertEquals(1, registrar.unregisterCount)
    }

    @Test
    fun mapsUnavailableHostIntoNonThrowingErrorState() = runTest {
        val runtime = ControllerWebServerRuntime(
            controller = WebServerController(RuntimeUnavailableHost),
            scope = this,
        )

        runtime.start(port = 8080, localhostOnly = true)
        advanceUntilIdle()

        assertFalse(runtime.state.value.isRunning)
        assertFalse(runtime.state.value.isLoading)
        assertEquals("Web server hosting is unavailable", runtime.state.value.error)
    }
}

private class RuntimeHost : WebServerHost {
    var stopCount: Int = 0

    override suspend fun start(config: WebServerConfig): WebServerHostStartResult =
        WebServerHostStartResult.Running(WebServerEndpoint("0.0.0.0", 43123))

    override suspend fun stop() {
        stopCount += 1
    }
}

private object RuntimeUnavailableHost : WebServerHost {
    override suspend fun start(config: WebServerConfig): WebServerHostStartResult =
        WebServerHostStartResult.Unavailable("Web server hosting is unavailable")

    override suspend fun stop() = Unit
}

private class RuntimeRegistrar : ServiceRegistrar {
    var registerCount: Int = 0
    var unregisterCount: Int = 0

    override suspend fun register(registration: ServiceRegistration): Result<RegisteredServiceInfo> {
        registerCount += 1
        return Result.success(
            RegisteredServiceInfo(
                serviceName = registration.serviceName,
                hostname = "rikkahub.local",
                port = registration.port,
                address = "192.0.2.1",
            )
        )
    }

    override suspend fun unregister(): Result<Unit> {
        unregisterCount += 1
        return Result.success(Unit)
    }
}
