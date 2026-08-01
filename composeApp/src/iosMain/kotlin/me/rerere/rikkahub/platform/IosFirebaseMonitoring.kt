@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package me.rerere.rikkahub.platform

import platform.Foundation.NSBundle
import platform.Foundation.NSError
import platform.Foundation.NSLocalizedDescriptionKey
import swiftPMImport.rikkahub.composeApp.FIRAnalytics
import swiftPMImport.rikkahub.composeApp.FIRApp
import swiftPMImport.rikkahub.composeApp.FIRCrashlytics
import swiftPMImport.rikkahub.composeApp.FIROptions

public class IosFirebaseAnalyticsTracker : AnalyticsTracker {
    override fun trackEvent(
        name: String,
        parameters: Map<String, String>,
    ) {
        if (!IosFirebaseAvailability.ensureConfigured()) return
        FIRAnalytics.logEventWithName(
            name = name,
            parameters = parameters
                .takeIf { it.isNotEmpty() }
                ?.map { (key, value) -> key as Any? to value as Any? }
                ?.toMap(),
        )
    }
}

public class IosFirebaseCrashReporter : CrashReporter {
    override fun recordException(throwable: Throwable) {
        if (!IosFirebaseAvailability.ensureConfigured()) return
        val error = NSError.errorWithDomain(
            domain = ERROR_DOMAIN,
            code = 0,
            userInfo = mapOf(
                NSLocalizedDescriptionKey to (throwable.message ?: throwable::class.simpleName.orEmpty()),
                STACK_TRACE_KEY to throwable.stackTraceToString(),
            ),
        )
        FIRCrashlytics.crashlytics().recordError(error)
    }

    private companion object {
        const val ERROR_DOMAIN = "me.rerere.rikkahub.kotlin"
        const val STACK_TRACE_KEY = "kotlin_stack_trace"
    }
}

private object IosFirebaseAvailability {
    private var initialized = false
    private var available = false

    fun ensureConfigured(): Boolean {
        if (initialized) return available
        initialized = true
        available = runCatching {
            if (FIRApp.defaultApp() != null) return@runCatching true
            val plistPath = NSBundle.mainBundle.pathForResource(
                name = "GoogleService-Info",
                ofType = "plist",
            ) ?: return@runCatching false
            val options = FIROptions(plistPath)
            FIRApp.configureWithOptions(options)
            true
        }.getOrDefault(false)
        return available
    }
}
