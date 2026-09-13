package me.rerere.rikkahub.utils

import androidx.compose.ui.Modifier
import io.github.vinceglb.filekit.PlatformFile

// Return true to consume an item; unconsumed content keeps the text field's default handling.
expect fun Modifier.onReceiveContent(
    onImage: (PlatformFile) -> Boolean,
    onText: (String) -> Boolean,
): Modifier
