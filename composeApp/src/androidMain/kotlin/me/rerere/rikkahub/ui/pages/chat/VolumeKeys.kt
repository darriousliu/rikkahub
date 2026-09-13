package me.rerere.rikkahub.ui.pages.chat

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable

@Composable
actual fun rememberVolumeKeyEventSource(): VolumeKeyEventSource? = LocalActivity.current as? VolumeKeyEventSource
