package me.rerere.common.utils

import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry

actual fun provideClipEntry(label: String?, text: String): ClipEntry {
    return ClipEntry(clipData = ClipData.newPlainText(label, text))
}

actual fun ClipEntry.getText(): String {
    return clipData.getText()
}

fun ClipData.getText(): String {
    return buildString {
        repeat(itemCount) {
            append(getItemAt(it).text ?: "")
        }
    }
}
