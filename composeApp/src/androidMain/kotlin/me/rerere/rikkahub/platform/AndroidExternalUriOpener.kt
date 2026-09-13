package me.rerere.rikkahub.platform

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri

class AndroidExternalUriOpener(
    private val context: Context,
) : ExternalUriOpener {
    override fun open(uri: String): Result<Unit> = runCatching {
        val intent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
        if (context !is Activity) {
            intent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        intent.launchUrl(context, uri.toUri())
    }
}
