package me.rerere.tts.controller

import kotlinx.coroutines.flow.StateFlow
import me.rerere.speech.utils.pcmToWav
import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.PlaybackState
import me.rerere.tts.model.TTSResponse

interface PlatformAudioPlayer {
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

internal fun audioBytesForPlayback(response: TTSResponse): ByteArray =
    if (response.format == AudioFormat.PCM) {
        pcmToWav(response.audioData, response.sampleRate ?: 24_000)
    } else {
        response.audioData
    }
