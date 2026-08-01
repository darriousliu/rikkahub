package me.rerere.rikkahub.platform

public const val DEFAULT_SERVICE_NAME: String = "rikkahub"
public const val DEFAULT_SERVICE_TYPE: String = "_http._tcp.local."

public data class ServiceRegistration(
    val port: Int,
    val serviceName: String = DEFAULT_SERVICE_NAME,
    val serviceType: String = DEFAULT_SERVICE_TYPE,
    val description: String = "RikkaHub Web Server",
) {
    init {
        require(port in 1..65535) { "Service port must be between 1 and 65535" }
        require(serviceName.isNotBlank()) { "Service name must not be blank" }
        require(serviceType.isNotBlank()) { "Service type must not be blank" }
    }
}

public data class RegisteredServiceInfo(
    val serviceName: String,
    val hostname: String,
    val port: Int,
    val address: String? = null,
)

public interface ServiceRegistrar {
    public suspend fun register(registration: ServiceRegistration): Result<RegisteredServiceInfo>

    public suspend fun unregister(): Result<Unit>
}
