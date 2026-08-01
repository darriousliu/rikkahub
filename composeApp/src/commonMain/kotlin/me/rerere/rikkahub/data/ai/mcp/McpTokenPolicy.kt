package me.rerere.rikkahub.data.ai.mcp

import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val TOKEN_REFRESH_LEEWAY = 1.minutes

class McpTokenPolicy(
    private val clock: Clock = Clock.System,
) {
    fun needsRefresh(oauth: McpOAuthState): Boolean {
        if (!oauth.enabled || oauth.refreshToken.isNullOrBlank()) return false

        val expired = oauth.expiresAt > 0 &&
            currentEpochMilliseconds() >= oauth.expiresAt - TOKEN_REFRESH_LEEWAY.inWholeMilliseconds
        return oauth.accessToken.isNullOrBlank() || expired
    }

    fun computeExpiry(expiresInSeconds: Long?): Long =
        if (expiresInSeconds != null && expiresInSeconds > 0) {
            currentEpochMilliseconds() + expiresInSeconds.seconds.inWholeMilliseconds
        } else {
            0L
        }

    private fun currentEpochMilliseconds(): Long = clock.now().toEpochMilliseconds()
}
