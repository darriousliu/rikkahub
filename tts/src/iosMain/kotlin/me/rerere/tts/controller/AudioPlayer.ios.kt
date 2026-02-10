package me.rerere.tts.controller

import io.github.vinceglb.filekit.utils.toNSData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import me.rerere.common.PlatformContext
import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.PlaybackState
import me.rerere.tts.model.PlaybackStatus
import me.rerere.tts.model.TTSResponse
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioPlayerDelegateProtocol
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.Foundation.NSError
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

actual class AudioPlayer actual constructor(context: PlatformContext) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _playbackState = MutableStateFlow(PlaybackState())
    actual val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var player: AVAudioPlayer? = null
    private var positionJob: Job? = null
    private var currentDelegate: PlayerDelegate? = null

    init {
        // 激活音频会话
        try {
            val session = AVAudioSession.sharedInstance()
            session.setCategory(AVAudioSessionCategoryPlayback, error = null)
            session.setActive(true, error = null)
        } catch (_: Exception) {
            // ignore
        }
    }

    actual fun pause() {
        player?.pause()
        stopPositionUpdates()
        _playbackState.update { it.copy(status = PlaybackStatus.Paused) }
    }

    actual fun resume() {
        player?.play()
        startPositionUpdates()
        _playbackState.update { it.copy(status = PlaybackStatus.Playing) }
    }

    actual fun stop() {
        player?.stop()
        stopPositionUpdates()
        _playbackState.update {
            it.copy(
                status = PlaybackStatus.Idle,
                positionMs = 0L
            )
        }
    }

    actual fun clear() {
        stop()
        player = null
        currentDelegate = null
    }

    actual fun release() {
        clear()
        scope.coroutineContext[Job]?.cancel()
    }

    actual fun seekBy(ms: Long) {
        val p = player ?: return
        val newTime = (p.currentTime + ms / 1000.0).coerceIn(0.0, p.duration)
        p.currentTime = newTime
        _playbackState.update { it.copy(positionMs = (newTime * 1000).toLong()) }
    }

    actual fun setSpeed(speed: Float) {
        player?.let {
            it.enableRate = true
            it.rate = speed
        }
        _playbackState.update { it.copy(speed = speed) }
    }

    actual suspend fun play(response: TTSResponse) = suspendCancellableCoroutine { cont ->
        val bytes = if (response.format == AudioFormat.PCM) {
            pcmToWav(response.audioData, response.sampleRate ?: 24000)
        } else {
            response.audioData
        }

        val nsData = bytes.toNSData()

        val audioPlayer = try {
            AVAudioPlayer(data = nsData, error = null)
        } catch (e: Exception) {
            cont.resumeWithException(
                RuntimeException("Failed to create AVAudioPlayer: ${e.message}")
            )
            return@suspendCancellableCoroutine
        }

        // 清理旧的
        player?.stop()
        stopPositionUpdates()

        player = audioPlayer
        audioPlayer.enableRate = true
        audioPlayer.rate = _playbackState.value.speed

        val delegate = PlayerDelegate(
            onFinish = {
                stopPositionUpdates()
                _playbackState.update {
                    it.copy(
                        status = PlaybackStatus.Ended,
                        positionMs = (audioPlayer.duration * 1000).toLong()
                    )
                }
                if (cont.isActive) cont.resume(Unit)
            },
            onError = { error ->
                stopPositionUpdates()
                val msg = error?.localizedDescription ?: "Unknown playback error"
                _playbackState.update {
                    it.copy(status = PlaybackStatus.Error, errorMessage = msg)
                }
                if (cont.isActive) {
                    cont.resumeWithException(RuntimeException(msg))
                }
            }
        )
        currentDelegate = delegate
        audioPlayer.delegate = delegate

        val durationMs = if (response.duration != null) {
            (response.duration * 1000).toLong()
        } else {
            (audioPlayer.duration * 1000).toLong()
        }

        _playbackState.update {
            it.copy(
                status = PlaybackStatus.Playing,
                positionMs = 0L,
                durationMs = durationMs
            )
        }

        audioPlayer.prepareToPlay()
        audioPlayer.play()
        startPositionUpdates()

        cont.invokeOnCancellation {
            audioPlayer.stop()
            stopPositionUpdates()
            _playbackState.update { it.copy(status = PlaybackStatus.Idle) }
        }
    }

    // --- 位置轮询 ---

    private fun startPositionUpdates() {
        if (positionJob?.isActive == true) return
        positionJob = scope.launch {
            while (true) {
                val p = player
                if (p != null && p.isPlaying()) {
                    val pos = (p.currentTime * 1000).toLong()
                    val dur = (p.duration * 1000).toLong()
                    _playbackState.update {
                        it.copy(
                            positionMs = pos,
                            durationMs = if (dur > 0) dur else it.durationMs
                        )
                    }
                }
                delay(100)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionJob?.cancel()
        positionJob = null
    }

    // --- PCM -> WAV ---

    private fun pcmToWav(
        pcm: ByteArray,
        sampleRate: Int,
        channels: Int = 1,
        bitsPerSample: Int = 16
    ): ByteArray {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val out = Buffer()
        with(out) {
            write("RIFF".encodeToByteArray())
            write(intToBytes(36 + pcm.size))
            write("WAVE".encodeToByteArray())
            write("fmt ".encodeToByteArray())
            write(intToBytes(16))
            write(shortToBytes(1))
            write(shortToBytes(channels.toShort()))
            write(intToBytes(sampleRate))
            write(intToBytes(byteRate))
            write(shortToBytes((channels * bitsPerSample / 8).toShort()))
            write(shortToBytes(bitsPerSample.toShort()))
            write("data".encodeToByteArray())
            write(intToBytes(pcm.size))
            write(pcm)
        }
        return out.readByteArray()
    }

    private fun intToBytes(value: Int) = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 24) and 0xFF).toByte()
    )

    private fun shortToBytes(value: Short) = byteArrayOf(
        (value.toInt() and 0xFF).toByte(),
        ((value.toInt() shr 8) and 0xFF).toByte()
    )
}

// --- AVAudioPlayerDelegate ---

private class PlayerDelegate(
    private val onFinish: () -> Unit,
    private val onError: (NSError?) -> Unit
) : NSObject(), AVAudioPlayerDelegateProtocol {

    override fun audioPlayerDidFinishPlaying(
        player: AVAudioPlayer,
        successfully: Boolean
    ) {
        if (successfully) {
            onFinish()
        } else {
            onError(null)
        }
    }

    override fun audioPlayerDecodeErrorDidOccur(
        player: AVAudioPlayer,
        error: NSError?
    ) {
        onError(error)
    }
}
