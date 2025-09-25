package me.rerere.tts.controller

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.rerere.common.PlatformContext
import me.rerere.tts.model.PlaybackState
import me.rerere.tts.model.TTSResponse

actual class AudioPlayer actual constructor(context: PlatformContext) {
    private val _playbackState = MutableStateFlow(PlaybackState())
    actual val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    actual fun pause() {
    }

    actual fun resume() {
    }

    actual fun stop() {
    }

    actual fun clear() {
    }

    actual fun release() {
    }

    actual fun seekBy(ms: Long) {
    }

    actual fun setSpeed(speed: Float) {
    }

    actual suspend fun play(response: TTSResponse) {
    }
}
