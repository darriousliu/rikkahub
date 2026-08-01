package me.rerere.tts.controller

import kotlinx.coroutines.flow.StateFlow
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
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

internal fun pcmToWav(
    pcm: ByteArray,
    sampleRate: Int,
    channels: Int = 1,
    bitsPerSample: Int = 16,
): ByteArray {
    val byteRate = sampleRate * channels * bitsPerSample / 8
    return Buffer().run {
        write("RIFF".encodeToByteArray())
        writeLittleEndian(36 + pcm.size)
        write("WAVE".encodeToByteArray())
        write("fmt ".encodeToByteArray())
        writeLittleEndian(16)
        writeLittleEndian(1.toShort())
        writeLittleEndian(channels.toShort())
        writeLittleEndian(sampleRate)
        writeLittleEndian(byteRate)
        writeLittleEndian((channels * bitsPerSample / 8).toShort())
        writeLittleEndian(bitsPerSample.toShort())
        write("data".encodeToByteArray())
        writeLittleEndian(pcm.size)
        write(pcm)
        readByteArray()
    }
}

private fun Buffer.writeLittleEndian(value: Int) {
    write(
        byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte(),
        )
    )
}

private fun Buffer.writeLittleEndian(value: Short) {
    write(
        byteArrayOf(
            (value.toInt() and 0xFF).toByte(),
            ((value.toInt() shr 8) and 0xFF).toByte(),
        )
    )
}
