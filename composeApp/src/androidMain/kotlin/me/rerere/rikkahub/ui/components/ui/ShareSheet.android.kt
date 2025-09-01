package me.rerere.rikkahub.ui.components.ui

import android.content.Intent
import me.rerere.common.PlatformContext

actual fun shareModel(context: PlatformContext, state: ShareSheetState) {
    val intent = Intent(Intent.ACTION_SEND)
    intent.type = "text/plain"
    intent.putExtra(
        Intent.EXTRA_TEXT,
        state.currentProvider?.encodeForShare() ?: ""
    )
    try {
        context.startActivity(Intent.createChooser(intent, null))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
