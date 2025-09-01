package me.rerere.common.utils

import androidx.compose.ui.platform.ClipEntry

actual fun provideClipEntry(label: String?, text: String): ClipEntry {
    return ClipEntry.withPlainText(text = text)
}

actual fun ClipEntry.getText(): String {
    return getPlainText().orEmpty()
}
