package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.runtime.Composable

interface VolumeKeyEventSource {
    fun addListener(listener: (isVolumeUp: Boolean) -> Boolean)
    fun removeListener(listener: (isVolumeUp: Boolean) -> Boolean)
}

@Composable
expect fun rememberVolumeKeyEventSource(): VolumeKeyEventSource?
