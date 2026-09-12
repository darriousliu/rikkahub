package me.rerere.rikkahub.utils

import androidx.compose.ui.platform.ClipEntry

internal expect fun createPlainTextClipEntry(label: String, text: String): ClipEntry
