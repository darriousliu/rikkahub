package me.rerere.rikkahub.data.ai.mcp

import kotlin.time.Clock

private const val TOKEN_REFRESH_LEEWAY_MS = 60_000L

internal class McpTokenPolicy private constructor(
    private val currentTimeMillis: () -> Long,
) {
    constructor() : this(System::currentTimeMillis)

    internal constructor(clock: Clock) : this(
        currentTimeMillis = { clock.now().toEpochMilliseconds() },
    )

    fun needsRefresh(oauth: McpOAuthState): Boolean {
        if (!oauth.enabled || oauth.refreshToken.isNullOrBlank()) return false

        val expired = oauth.expiresAt > 0 &&
            currentTimeMillis() >= oauth.expiresAt - TOKEN_REFRESH_LEEWAY_MS
        return oauth.accessToken.isNullOrBlank() || expired
    }

    fun computeExpiry(expiresInSeconds: Long?): Long =
        if (expiresInSeconds != null && expiresInSeconds > 0) {
            currentTimeMillis() + expiresInSeconds * 1_000
        } else {
            0L
        }
}
