package me.rerere.rikkahub.di

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.engine.darwin.KtorNSURLSessionDelegate
import platform.Foundation.NSURLNetworkServiceTypeBackground
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration

actual fun httpClientEngine(): HttpClientEngine {
    return Darwin.create {
        configureRequest {
            setAllowsCellularAccess(true)
            setAllowsExpensiveNetworkAccess(true)
            setAllowsConstrainedNetworkAccess(true)

            setNetworkServiceType(NSURLNetworkServiceTypeBackground)
        }
        val config = NSURLSessionConfiguration.backgroundSessionConfigurationWithIdentifier(
            "me.rerere.rikkahub.background"
        )
        config.discretionary = true
        config.sessionSendsLaunchEvents = true

        val delegate = KtorNSURLSessionDelegate()
        val backgroundSession = NSURLSession.sessionWithConfiguration(
            configuration = config,
            delegate = delegate,
            delegateQueue = null
        )

        usePreconfiguredSession(backgroundSession, delegate)
    }
}
