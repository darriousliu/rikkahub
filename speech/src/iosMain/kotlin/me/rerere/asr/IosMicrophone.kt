package me.rerere.asr

import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.get
import kotlinx.cinterop.pointed
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.value
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import platform.AVFAudio.AVAudioConverter
import platform.AVFAudio.AVAudioConverterInputStatus_HaveData
import platform.AVFAudio.AVAudioConverterInputStatus_NoDataNow
import platform.AVFAudio.AVAudioConverterOutputStatus_Error
import platform.AVFAudio.AVAudioConverterPrimeMethod_None
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioPCMFormatInt16
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioSessionCategoryOptionDefaultToSpeaker
import platform.AVFAudio.AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation
import platform.AVFAudio.setActive
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.authorizationStatusForMediaType
import kotlin.math.ceil

class IosMicrophone : Microphone {
    override val hasPermission: Boolean
        get() = AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeAudio) == AVAuthorizationStatusAuthorized

    // The recorder activates/deactivates AVAudioSession with the native recording resources.
    override fun requestAudioFocus(): Boolean = true
    override fun abandonAudioFocus() = Unit
    override fun createRecorder(sampleRate: Int, minBufferSize: Int): PcmRecorder =
        IosPcmRecorder(sampleRate, minBufferSize)
}

@OptIn(ExperimentalForeignApi::class)
private class IosPcmRecorder(private val sampleRate: Int, override val bufferSize: Int) : PcmRecorder {
    private val lock = reentrantLock()
    private val frames = Channel<ByteArray>(Channel.UNLIMITED)
    private var engine: AVAudioEngine? = null
    private var sessionActive = false
    private var stopped = false
    private var pending = ByteArray(0)
    private var pendingOffset = 0

    override fun startRecording() = lock.withLock {
        if (stopped) throw CancellationException("Recording stopped")
        val session = AVAudioSession.sharedInstance()
        check(session.setCategory(
            AVAudioSessionCategoryPlayAndRecord,
            withOptions = AVAudioSessionCategoryOptionDefaultToSpeaker,
            error = null,
        )) { "Failed to configure microphone audio session" }
        check(session.setActive(true, error = null)) { "Failed to activate microphone audio session" }
        sessionActive = true

        val audioEngine = AVAudioEngine()
        val input = audioEngine.inputNode
        val inputFormat = input.outputFormatForBus(0u)
        check(inputFormat.sampleRate > 0 && inputFormat.channelCount > 0u) { "Microphone is unavailable" }
        val outputFormat = AVAudioFormat(
            commonFormat = AVAudioPCMFormatInt16,
            sampleRate = sampleRate.toDouble(),
            channels = 1u,
            interleaved = true,
        )
        val converter = AVAudioConverter(inputFormat, outputFormat)
        converter.primeMethod = AVAudioConverterPrimeMethod_None

        // The hardware commonly supplies 48 kHz float PCM; convert instead of labeling it as 16/24 kHz PCM16.
        input.installTapOnBus(0u, bufferSize = 1024u, format = inputFormat) { buffer, _ ->
            if (buffer != null) {
                val capacity = ceil(buffer.frameLength.toDouble() * sampleRate / inputFormat.sampleRate).toUInt() + 1u
                val output = AVAudioPCMBuffer(outputFormat, frameCapacity = capacity)
                var supplied = false
                val status = converter.convertToBuffer(output, error = null) { _, inputStatus ->
                    if (supplied) {
                        inputStatus?.pointed?.value = AVAudioConverterInputStatus_NoDataNow
                        null
                    } else {
                        supplied = true
                        inputStatus?.pointed?.value = AVAudioConverterInputStatus_HaveData
                        buffer
                    }
                }
                if (status == AVAudioConverterOutputStatus_Error) {
                    frames.close(IllegalStateException("Microphone PCM conversion failed"))
                } else if (output.frameLength > 0u) {
                    output.int16ChannelData?.get(0)?.reinterpret<kotlinx.cinterop.ByteVar>()
                        ?.readBytes(output.frameLength.toInt() * 2)?.let { frames.trySend(it) }
                }
            }
        }
        engine = audioEngine
        audioEngine.prepare()
        check(audioEngine.startAndReturnError(null)) { "Failed to start microphone recording" }
    }

    override suspend fun read(buffer: ByteArray, offset: Int, size: Int): Int {
        if (pendingOffset == pending.size) {
            pending = frames.receive()
            pendingOffset = 0
        }
        val count = minOf(size, pending.size - pendingOffset)
        pending.copyInto(buffer, offset, pendingOffset, pendingOffset + count)
        pendingOffset += count
        return count
    }

    override fun stop() = lock.withLock {
        stopped = true
        engine?.let {
            it.stop()
            it.inputNode.removeTapOnBus(0u)
        }
        engine = null
        frames.cancel(CancellationException("Recording stopped"))
        if (sessionActive) {
            AVAudioSession.sharedInstance().setActive(
                false,
                withOptions = AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation,
                error = null,
            )
            sessionActive = false
        }
    }

    override fun release() = stop()
}
