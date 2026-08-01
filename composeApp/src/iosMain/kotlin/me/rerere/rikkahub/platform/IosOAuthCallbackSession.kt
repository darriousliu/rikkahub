package me.rerere.rikkahub.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.AuthenticationServices.ASPresentationAnchor
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

public class IosOAuthCallbackSessionFactory : OAuthCallbackSessionFactory {
    override suspend fun create(): OAuthCallbackSession = IosOAuthCallbackSession()
}

private class IosOAuthCallbackSession : OAuthCallbackSession {
    override val redirectUri: String = REDIRECT_URI

    private val presentationContextProvider = OAuthPresentationContextProvider()
    private var session: ASWebAuthenticationSession? = null

    override suspend fun authorize(
        authorizationUri: String,
        expectedState: String,
    ): OAuthCallback = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val authorizationUrl = NSURL.URLWithString(authorizationUri)
            if (authorizationUrl == null) {
                continuation.resumeWithException(IllegalArgumentException("Invalid OAuth authorization URI"))
                return@suspendCancellableCoroutine
            }

            val activeSession = ASWebAuthenticationSession(
                uRL = authorizationUrl,
                callbackURLScheme = REDIRECT_SCHEME,
            ) { callbackUrl, error ->
                if (!continuation.isActive) return@ASWebAuthenticationSession
                when {
                    error != null -> continuation.resumeWithException(
                        IllegalStateException(error.localizedDescription)
                    )

                    callbackUrl != null -> continuation.resume(
                        parseOAuthCallbackUri(callbackUrl.absoluteString ?: "")
                    )

                    else -> continuation.resumeWithException(
                        IllegalStateException("OAuth callback did not return a URL")
                    )
                }
            }
            activeSession.presentationContextProvider = presentationContextProvider
            session = activeSession
            continuation.invokeOnCancellation { activeSession.cancel() }
            if (!activeSession.start() && continuation.isActive) {
                continuation.resumeWithException(IllegalStateException("Unable to start OAuth session"))
            }
        }
    }

    override suspend fun close() = withContext(Dispatchers.Main) {
        session?.cancel()
        session = null
    }

    private companion object {
        const val REDIRECT_SCHEME = "rikkahub"
        const val REDIRECT_URI = "rikkahub://mcp-oauth-callback"
    }
}

private class OAuthPresentationContextProvider : NSObject(),
    ASWebAuthenticationPresentationContextProvidingProtocol {
    override fun presentationAnchorForWebAuthenticationSession(
        session: ASWebAuthenticationSession,
    ): ASPresentationAnchor = UIApplication.sharedApplication.keyWindow ?: UIWindow()
}
