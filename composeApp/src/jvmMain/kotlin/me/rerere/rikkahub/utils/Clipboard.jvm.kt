package me.rerere.rikkahub.utils

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry
import java.awt.datatransfer.StringSelection

@OptIn(ExperimentalComposeUiApi::class)
internal actual fun createPlainTextClipEntry(label: String, text: String): ClipEntry =
    ClipEntry(StringSelection(text))
