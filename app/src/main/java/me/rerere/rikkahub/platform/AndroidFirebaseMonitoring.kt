package me.rerere.rikkahub.platform

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

class AndroidFirebaseAnalyticsTracker(
    private val analytics: FirebaseAnalytics,
) : AnalyticsTracker {
    override fun trackEvent(
        name: String,
        parameters: Map<String, String>,
    ) {
        val bundle = parameters.takeIf { it.isNotEmpty() }?.let { values ->
            Bundle().apply {
                values.forEach { (key, value) -> putString(key, value) }
            }
        }
        analytics.logEvent(name, bundle)
    }
}

class AndroidFirebaseCrashReporter(
    private val crashlytics: FirebaseCrashlytics,
) : CrashReporter {
    override fun recordException(throwable: Throwable) {
        crashlytics.recordException(throwable)
    }
}
