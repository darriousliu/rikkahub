package me.rerere.rikkahub.platform

import android.content.Context
import android.net.wifi.WifiManager
import java.net.InetAddress
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

public class AndroidJmDnsServiceRegistrar(
    context: Context,
) : ServiceRegistrar {
    private val applicationContext = context.applicationContext
    private var jmdns: JmDNS? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    override suspend fun register(
        registration: ServiceRegistration,
    ): Result<RegisteredServiceInfo> = withContext(Dispatchers.IO) {
        cleanup().fold(
            onSuccess = {
                runCatching {
                    val wifiManager = applicationContext
                        .getSystemService(Context.WIFI_SERVICE) as? WifiManager
                    multicastLock = wifiManager?.createMulticastLock(MULTICAST_LOCK_TAG)?.apply {
                        setReferenceCounted(true)
                        acquire()
                    }
                    val address = getLocalIpAddress(wifiManager)
                        ?: error("Failed to get local IP address")
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
        val closeResult = runCatching {
            jmdns?.unregisterAllServices()
            jmdns?.close()
        }
        jmdns = null
        val lockResult = runCatching {
            if (multicastLock?.isHeld == true) multicastLock?.release()
        }
        multicastLock = null
        return closeResult.fold(
            onSuccess = { lockResult },
            onFailure = { Result.failure(it) },
        )
    }

    @Suppress("DEPRECATION")
    private fun getLocalIpAddress(wifiManager: WifiManager?): InetAddress? {
        val ipAddress = wifiManager?.connectionInfo?.ipAddress ?: return null
        if (ipAddress == 0) return null
        return InetAddress.getByAddress(
            byteArrayOf(
                (ipAddress and 0xff).toByte(),
                (ipAddress shr 8 and 0xff).toByte(),
                (ipAddress shr 16 and 0xff).toByte(),
                (ipAddress shr 24 and 0xff).toByte(),
            ),
        )
    }

    private companion object {
        const val MULTICAST_LOCK_TAG = "rikkahub-jmdns-lock"
    }
}
