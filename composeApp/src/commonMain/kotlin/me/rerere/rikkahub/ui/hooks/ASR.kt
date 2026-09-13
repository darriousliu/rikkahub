package me.rerere.rikkahub.ui.hooks

import kotlinx.coroutines.flow.StateFlow
import me.rerere.asr.ASRState

interface CustomAsrState {
    val state: StateFlow<ASRState>
    fun start(onTranscriptChange: (String) -> Unit)
    fun stop()
    fun cleanup()
}
