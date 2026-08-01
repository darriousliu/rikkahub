package me.rerere.rikkahub.web

class UnavailableWebServerHost(
    private val reason: String = "Web server hosting is unavailable on iOS",
) : WebServerHost {
    override suspend fun start(config: WebServerConfig): WebServerHostStartResult =
        WebServerHostStartResult.Unavailable(reason)

    override suspend fun stop() = Unit
}
