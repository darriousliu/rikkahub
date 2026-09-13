package me.rerere.rikkahub.utils

import androidx.compose.foundation.content.MediaType
import androidx.compose.foundation.content.ReceiveContentListener
import androidx.compose.foundation.content.consume
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.content.hasMediaType
import androidx.compose.ui.Modifier
import io.github.vinceglb.filekit.PlatformFile

actual fun Modifier.onReceiveContent(
    onImage: (PlatformFile) -> Boolean,
    onText: (String) -> Boolean,
): Modifier = contentReceiver(ReceiveContentListener { content ->
    when {
        content.hasMediaType(MediaType.Image) -> content.consume { item ->
            item.uri?.let { onImage(PlatformFile(it)) } ?: false
        }
        content.hasMediaType(MediaType.Text) -> content.consume { item ->
            item.text?.toString()?.let(onText) ?: false
        }
        else -> content
    }
})
