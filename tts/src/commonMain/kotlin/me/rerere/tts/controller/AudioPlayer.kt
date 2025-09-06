package me.rerere.tts.controller

import kotlinx.coroutines.flow.StateFlow
import me.rerere.common.PlatformContext
import me.rerere.tts.model.PlaybackState
import me.rerere.tts.model.TTSResponse

expect class AudioPlayer(context: PlatformContext) {
    val playbackState: StateFlow<PlaybackState>
    fun pause()
    fun resume()
    fun stop()
    fun clear()
    fun release()
    fun seekBy(ms: Long)
    fun setSpeed(speed: Float)

    suspend fun play(response: TTSResponse)
}
