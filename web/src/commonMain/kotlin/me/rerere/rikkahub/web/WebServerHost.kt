package me.rerere.rikkahub.web

/** Platform socket and Ktor engine operations; lifecycle decisions stay in WebServerManager. */
interface WebServerHost {
    fun isPortAvailable(port: Int): Boolean

    suspend fun start(port: Int, host: String)

    suspend fun stop(gracePeriodMillis: Long, timeoutMillis: Long)
}
