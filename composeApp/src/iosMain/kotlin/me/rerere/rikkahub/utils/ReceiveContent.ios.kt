package me.rerere.rikkahub.utils

import androidx.compose.ui.Modifier
import io.github.vinceglb.filekit.PlatformFile

actual fun Modifier.onReceiveContent(
    onImage: (PlatformFile) -> Boolean,
    onText: (String) -> Boolean,
): Modifier = this
