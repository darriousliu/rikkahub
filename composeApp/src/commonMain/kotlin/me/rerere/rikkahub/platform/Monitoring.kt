package me.rerere.rikkahub.platform

public fun interface AnalyticsTracker {
    public fun trackEvent(
        name: String,
        parameters: Map<String, String>,
    )
}

public fun AnalyticsTracker.trackEvent(name: String) {
    trackEvent(name, emptyMap())
}

public fun interface CrashReporter {
    public fun recordException(throwable: Throwable)
}

public object NoOpMonitoring : AnalyticsTracker, CrashReporter {
    override fun trackEvent(name: String, parameters: Map<String, String>) = Unit

    override fun recordException(throwable: Throwable) = Unit
}
