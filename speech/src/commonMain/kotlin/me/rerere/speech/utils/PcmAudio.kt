package me.rerere.speech.utils

import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.io.writeIntLe
import kotlinx.io.writeShortLe

internal fun pcmToWav(
    pcm: ByteArray,
    sampleRate: Int,
    channels: Int = 1,
    bitsPerSample: Int = 16,
): ByteArray {
    val byteRate = sampleRate * channels * bitsPerSample / 8
    return Buffer().run {
        write("RIFF".encodeToByteArray())
        writeIntLe(36 + pcm.size)
        write("WAVE".encodeToByteArray())
        write("fmt ".encodeToByteArray())
        writeIntLe(16)
        writeShortLe(1.toShort())
        writeShortLe(channels.toShort())
        writeIntLe(sampleRate)
        writeIntLe(byteRate)
        writeShortLe((channels * bitsPerSample / 8).toShort())
        writeShortLe(bitsPerSample.toShort())
        write("data".encodeToByteArray())
        writeIntLe(pcm.size)
        write(pcm)
        readByteArray()
    }
}
