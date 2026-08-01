package me.rerere.rikkahub.platform

import io.ktor.http.Url

public data class OAuthCallback(
    public val state: String?,
    public val code: String?,
    public val error: String?,
)

/** A single platform-owned OAuth browser/callback lifecycle. */
public interface OAuthCallbackSession {
    public val redirectUri: String

    public suspend fun authorize(
        authorizationUri: String,
        expectedState: String,
    ): OAuthCallback

    public suspend fun close()
}

public fun interface OAuthCallbackSessionFactory {
    public suspend fun create(): OAuthCallbackSession
}

public fun OAuthCallback.requireAuthorizationCode(expectedState: String): String {
    check(state == expectedState) { "OAuth state mismatch" }
    error?.let { error("OAuth authorization failed: $it") }
    return code ?: error("OAuth authorization did not return a code")
}

internal fun parseOAuthCallbackUri(uri: String): OAuthCallback {
    val parameters = Url(uri).parameters
    return OAuthCallback(
        state = parameters["state"],
        code = parameters["code"],
        error = parameters["error"],
    )
}
