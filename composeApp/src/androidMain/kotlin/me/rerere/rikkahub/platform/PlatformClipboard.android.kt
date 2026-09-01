package me.rerere.rikkahub.platform

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

internal actual object PlatformClipboard : KoinComponent {
    private val clipboardManager: ClipboardManager
        get() = get<Context>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    actual fun readText(): String {
        val clip = clipboardManager.primaryClip ?: return ""
        val item = clip.getItemAt(0) ?: return ""
        return item.text?.toString().orEmpty()
    }

    actual fun writeText(text: String) {
        clipboardManager.setPrimaryClip(ClipData.newPlainText("text", text))
    }
}
