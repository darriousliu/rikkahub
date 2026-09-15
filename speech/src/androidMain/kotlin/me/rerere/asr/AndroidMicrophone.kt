package me.rerere.asr

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException

class AndroidMicrophone(private val context: Context) : Microphone {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        .setAcceptsDelayedFocusGain(false)
        .build()

    override val hasPermission: Boolean
        get() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    override fun requestAudioFocus(): Boolean =
        audioManager.requestAudioFocus(audioFocusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED

    override fun abandonAudioFocus() {
        audioManager.abandonAudioFocusRequest(audioFocusRequest)
    }

    override fun createRecorder(sampleRate: Int, minBufferSize: Int): PcmRecorder =
        AndroidPcmRecorder(sampleRate, minBufferSize)
}

private class AndroidPcmRecorder(private val sampleRate: Int, minBufferSize: Int) : PcmRecorder {
    override val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
    ).coerceAtLeast(minBufferSize)
    @Volatile private var recorder: AudioRecord? = null
    private var stopped = false

    @SuppressLint("MissingPermission")
    @Synchronized
    override fun startRecording() {
        if (stopped) throw CancellationException("Recording stopped")
        val input = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize * 2
        )
        recorder = input
        input.startRecording()
    }

    override suspend fun read(buffer: ByteArray, offset: Int, size: Int): Int =
        recorder?.read(buffer, offset, size) ?: throw CancellationException("Recording stopped")

    @Synchronized
    override fun stop() {
        stopped = true
        recorder?.stop()
    }

    @Synchronized
    override fun release() {
        stopped = true
        recorder?.release()
        recorder = null
    }
}
