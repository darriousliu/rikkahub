package me.rerere.tts.player

import me.rerere.common.PlatformContext
import me.rerere.tts.model.AudioFormat

expect class AudioPlayer(context: PlatformContext) {
    suspend fun playSound(sound: ByteArray, format: AudioFormat)
    suspend fun playPcmSound(pcmData: ByteArray, sampleRate: Int = 24000)
    fun stop()
    fun dispose()
}
