package me.rerere.rikkahub.ui.pages.setting

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import coil3.PlatformContext

internal actual fun onShareClick(
    shareText: String,
    context: PlatformContext,
    share: String,
    noShareApp: String
) {
    val intent = Intent(Intent.ACTION_SEND)
    intent.type = "text/plain"
    intent.putExtra(Intent.EXTRA_TEXT, shareText)
    try {
        context.startActivity(Intent.createChooser(intent, share))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, noShareApp, Toast.LENGTH_SHORT).show()
    }
}
