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
        TODO("Not yet implemented")
    }

    actual fun resume() {
        TODO("Not yet implemented")
    }

    actual fun stop() {
        TODO("Not yet implemented")
    }

    actual fun clear() {
        TODO("Not yet implemented")
    }

    actual fun release() {
        TODO("Not yet implemented")
    }

    actual fun seekBy(ms: Long) {
        TODO("Not yet implemented")
    }

    actual fun setSpeed(speed: Float) {
        TODO("Not yet implemented")
    }

    actual suspend fun play(response: TTSResponse) {
        TODO("Not yet implemented")
    }
}
