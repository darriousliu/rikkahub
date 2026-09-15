package me.rerere.asr

/** Platform microphone access; recognition and transcript handling stay in the controllers. */
interface Microphone {
    val hasPermission: Boolean
    fun requestAudioFocus(): Boolean
    fun abandonAudioFocus()
    fun createRecorder(sampleRate: Int, minBufferSize: Int): PcmRecorder
}

/** Mono, signed PCM16 in little-endian byte order, at the requested sample rate. */
interface PcmRecorder {
    val bufferSize: Int
    fun startRecording()
    suspend fun read(buffer: ByteArray, offset: Int, size: Int): Int
    fun stop()
    fun release()
}
