package me.rerere.rikkahub.utils

import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry

internal actual fun createPlainTextClipEntry(label: String, text: String): ClipEntry =
    ClipEntry(clipData = ClipData.newPlainText(label, text))
