package me.rerere.rikkahub.platform

import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel

public class JvmSentryMonitoring : AnalyticsTracker, CrashReporter {
    override fun trackEvent(
        name: String,
        parameters: Map<String, String>,
    ) {
        val breadcrumb = Breadcrumb().apply {
            category = ANALYTICS_CATEGORY
            message = name
            level = SentryLevel.INFO
            parameters.forEach(::setData)
        }
        Sentry.addBreadcrumb(breadcrumb)
    }

    override fun recordException(throwable: Throwable) {
        Sentry.captureException(throwable)
    }

    private companion object {
        const val ANALYTICS_CATEGORY = "analytics"
    }
}
