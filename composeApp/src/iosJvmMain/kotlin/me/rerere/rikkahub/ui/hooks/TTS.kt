package me.rerere.rikkahub.ui.hooks

import androidx.compose.runtime.Composable
import org.koin.compose.koinInject

@Composable
actual fun rememberCustomTtsState(): CustomTtsState = rememberSharedCustomTtsState(
    settingsStore = koinInject(),
    ttsManager = koinInject(),
    audioPlayer = koinInject(),
)
