package me.rerere.rikkahub.utils

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry

@OptIn(ExperimentalComposeUiApi::class)
internal actual fun createPlainTextClipEntry(label: String, text: String): ClipEntry =
    ClipEntry.withPlainText(text)
