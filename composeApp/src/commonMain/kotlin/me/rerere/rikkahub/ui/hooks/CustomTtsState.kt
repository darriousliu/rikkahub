package me.rerere.rikkahub.ui.hooks

import kotlinx.coroutines.flow.StateFlow
import me.rerere.tts.model.PlaybackState

interface CustomTtsState {
    val isAvailable: StateFlow<Boolean>
    val isSpeaking: StateFlow<Boolean>
    val error: StateFlow<String?>
    val currentChunk: StateFlow<Int>
    val totalChunks: StateFlow<Int>
    val playbackState: StateFlow<PlaybackState>

    fun speak(text: String, flushCalled: Boolean = true)

    fun stop()

    fun pause()

    fun resume()

    fun skipNext()

    fun fastForward(ms: Long = 5_000)

    fun setSpeed(speed: Float)

    fun cleanup()
}
