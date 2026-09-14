package me.rerere.rikkahub.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.Clipboard
import io.github.vinceglb.filekit.PlatformFile

// Return true to consume an item; unconsumed content keeps the text field's default handling.
expect fun Modifier.onReceiveContent(
    onImage: (PlatformFile) -> Boolean,
    onText: (String) -> Boolean,
): Modifier

@Composable
expect fun rememberReceiveContentClipboard(
    onImage: (PlatformFile) -> Boolean,
    onText: (String) -> Boolean,
): Clipboard
