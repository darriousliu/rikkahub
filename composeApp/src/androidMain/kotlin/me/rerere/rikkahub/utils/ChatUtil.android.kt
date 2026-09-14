package me.rerere.rikkahub.utils

import android.content.Context
import me.rerere.ai.ui.UIMessage

fun Context.copyMessageToClipboard(message: UIMessage) {
    this.writeClipboardText(message.toText())
}
