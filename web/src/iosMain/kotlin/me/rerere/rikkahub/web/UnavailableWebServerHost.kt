package me.rerere.rikkahub.web

class UnavailableWebServerHost(
    private val reason: String = "Web server hosting is unavailable on iOS",
) : WebServerHost {
    override fun isPortAvailable(port: Int): Boolean = throw UnsupportedOperationException(reason)

    override suspend fun start(port: Int, host: String): Unit = throw UnsupportedOperationException(reason)

    override suspend fun stop(gracePeriodMillis: Long, timeoutMillis: Long) = Unit
}
