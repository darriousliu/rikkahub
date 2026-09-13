package me.rerere.rikkahub.ui.hooks

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.StateFlow
import me.rerere.asr.ASRState

interface CustomAsrState {
    val state: StateFlow<ASRState>
    fun start(onTranscriptChange: (String) -> Unit)
    fun stop()
    fun cleanup()
}

@Composable
expect fun rememberCustomAsrState(): CustomAsrState?
