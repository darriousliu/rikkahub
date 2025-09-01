package me.rerere.common.utils

import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry

actual fun provideClipEntry(label: String, text: String): ClipEntry {
    return ClipEntry(clipData = ClipData.newPlainText(label, text))
}
