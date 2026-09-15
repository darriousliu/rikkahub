package me.rerere.asr

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.TargetDataLine
import kotlinx.coroutines.CancellationException

class JvmMicrophone : Microphone {
    // The OS gates Java Sound when the input line opens (including macOS's permission prompt).
    override val hasPermission: Boolean get() = true
    override fun requestAudioFocus(): Boolean = true
    override fun abandonAudioFocus() = Unit

    override fun createRecorder(sampleRate: Int, minBufferSize: Int): PcmRecorder =
        JvmPcmRecorder(sampleRate, minBufferSize)
}

private class JvmPcmRecorder(sampleRate: Int, override val bufferSize: Int) : PcmRecorder {
    private val format = AudioFormat(sampleRate.toFloat(), 16, 1, true, false)
    @Volatile private var line: TargetDataLine? = null
    private var stopped = false

    @Synchronized
    override fun startRecording() {
        if (stopped) throw CancellationException("Recording stopped")
        val input = AudioSystem.getTargetDataLine(format)
        line = input
        input.open(format, bufferSize * 2)
        input.start()
    }

    override suspend fun read(buffer: ByteArray, offset: Int, size: Int): Int =
        line?.read(buffer, offset, size) ?: throw CancellationException("Recording stopped")

    @Synchronized
    override fun stop() {
        stopped = true
        line?.stop()
        line?.flush()
    }

    @Synchronized
    override fun release() {
        stopped = true
        line?.close()
        line = null
    }
}
