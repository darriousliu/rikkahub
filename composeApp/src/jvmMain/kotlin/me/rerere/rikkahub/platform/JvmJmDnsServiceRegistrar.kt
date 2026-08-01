package me.rerere.rikkahub.platform

import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

public class JvmJmDnsServiceRegistrar : ServiceRegistrar {
    private var jmdns: JmDNS? = null

    override suspend fun register(
        registration: ServiceRegistration,
    ): Result<RegisteredServiceInfo> = withContext(Dispatchers.IO) {
        cleanup().fold(
            onSuccess = {
                runCatching {
                    val address = findLocalAddress() ?: error("Failed to get local IP address")
                    val mdns = JmDNS.create(address, registration.serviceName)
                    jmdns = mdns
                    mdns.registerService(
                        ServiceInfo.create(
                            registration.serviceType,
                            registration.serviceName,
                            registration.port,
                            registration.description,
                        ),
                    )
                    RegisteredServiceInfo(
                        serviceName = registration.serviceName,
                        hostname = "${registration.serviceName}.local",
                        port = registration.port,
                        address = address.hostAddress,
                    )
                }.onFailure { cleanup() }
            },
            onFailure = { Result.failure(it) },
        )
    }

    override suspend fun unregister(): Result<Unit> = withContext(Dispatchers.IO) {
        cleanup()
    }

    private fun cleanup(): Result<Unit> {
        val currentJmDns = jmdns
        jmdns = null
        return runCatching {
            currentJmDns?.unregisterAllServices()
            currentJmDns?.close()
        }
    }

    private fun findLocalAddress(): InetAddress? {
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
        while (interfaces.hasMoreElements()) {
            val networkInterface = interfaces.nextElement()
            if (!networkInterface.isUp || networkInterface.isLoopback || networkInterface.isVirtual) continue
            val addresses = networkInterface.inetAddresses
            while (addresses.hasMoreElements()) {
                val address = addresses.nextElement()
                if (address is Inet4Address && !address.isLoopbackAddress) return address
            }
        }
        return null
    }
}
