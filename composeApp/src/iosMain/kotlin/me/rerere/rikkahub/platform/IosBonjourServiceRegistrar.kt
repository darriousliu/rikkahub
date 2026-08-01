package me.rerere.rikkahub.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSNetService

public class IosBonjourServiceRegistrar : ServiceRegistrar {
    private var service: NSNetService? = null

    override suspend fun register(
        registration: ServiceRegistration,
    ): Result<RegisteredServiceInfo> = withContext(Dispatchers.Main) {
        cleanup().fold(
            onSuccess = {
                runCatching {
                    val publishedService = NSNetService(
                        domain = BONJOUR_DOMAIN,
                        type = registration.serviceType.toBonjourType(),
                        name = registration.serviceName,
                        port = registration.port,
                    )
                    publishedService.publish()
                    service = publishedService
                    RegisteredServiceInfo(
                        serviceName = registration.serviceName,
                        hostname = "${registration.serviceName}.local",
                        port = registration.port,
                    )
                }
            },
            onFailure = { Result.failure(it) },
        )
    }

    override suspend fun unregister(): Result<Unit> = withContext(Dispatchers.Main) {
        cleanup()
    }

    private fun cleanup(): Result<Unit> {
        val currentService = service
        service = null
        return runCatching {
            currentService?.stop()
        }
    }
}

private fun String.toBonjourType(): String =
    removeSuffix("local.").trimEnd('.') + "."

private const val BONJOUR_DOMAIN = "local."
