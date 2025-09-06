package me.rerere.tts.player

import me.rerere.common.PlatformContext
import me.rerere.tts.model.AudioFormat

actual class AudioPlayer actual constructor(context: PlatformContext) {
    actual suspend fun playSound(sound: ByteArray, format: AudioFormat) {
    }

    actual suspend fun playPcmSound(pcmData: ByteArray, sampleRate: Int) {
    }

    actual fun stop() {
    }

    actual fun dispose() {
    }
}
