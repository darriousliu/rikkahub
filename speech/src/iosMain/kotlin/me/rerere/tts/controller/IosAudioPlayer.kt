package me.rerere.tts.controller

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import me.rerere.tts.model.PlaybackState
import me.rerere.tts.model.PlaybackStatus
import me.rerere.tts.model.TTSResponse
import me.rerere.tts.toNSData
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioPlayerDelegateProtocol
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.Foundation.NSError
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@OptIn(ExperimentalForeignApi::class)
class IosAudioPlayer : PlatformAudioPlayer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _playbackState = MutableStateFlow(PlaybackState())
    override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var player: AVAudioPlayer? = null
    private var delegate: AudioPlayerDelegate? = null
    private var continuation: CancellableContinuation<Unit>? = null
    private var positionJob: Job? = null
    private var playbackSpeed = 1.0f

    override fun pause() {
        scope.launch {
            player?.pause()
            stopPositionUpdates()
            _playbackState.update { it.copy(status = PlaybackStatus.Paused) }
        }
    }

    override fun resume() {
        scope.launch {
            if (player?.play() == true) {
                startPositionUpdates()
                _playbackState.update { it.copy(status = PlaybackStatus.Playing) }
            }
        }
    }

    override fun stop() {
        scope.launch {
            player?.stop()
            finishPlayback(PlaybackStatus.Idle)
        }
    }

    override fun clear() {
        scope.launch {
            disposeCurrentPlayer()
            _playbackState.value = PlaybackState(speed = playbackSpeed)
        }
    }

    override fun release() {
        scope.launch {
            disposeCurrentPlayer()
            AVAudioSession.sharedInstance().setActive(false, error = null)
            scope.cancel()
        }
    }

    override fun seekBy(ms: Long) {
        scope.launch {
            player?.let { current ->
                current.currentTime = (current.currentTime + ms / 1_000.0).coerceIn(0.0, current.duration)
                updatePosition(current)
            }
        }
    }

    override fun setSpeed(speed: Float) {
        playbackSpeed = speed
        _playbackState.update { it.copy(speed = speed) }
        scope.launch {
            player?.rate = speed
        }
    }

    override suspend fun play(response: TTSResponse) = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            disposeCurrentPlayer()
            continuation = cont
            _playbackState.update {
                it.copy(
                    status = PlaybackStatus.Buffering,
                    positionMs = 0L,
                    durationMs = response.duration?.times(1_000)?.toLong() ?: 0L,
                    errorMessage = null,
                )
            }

            cont.invokeOnCancellation {
                scope.launch {
                    if (continuation === cont) disposeCurrentPlayer()
                }
            }

            runCatching {
                AVAudioSession.sharedInstance().apply {
                    check(setCategory(AVAudioSessionCategoryPlayback, error = null)) {
                        "Failed to configure iOS audio session"
                    }
                    check(setActive(true, error = null)) {
                        "Failed to activate iOS audio session"
                    }
                }

                AVAudioPlayer(audioBytesForPlayback(response).toNSData(), error = null).also { audioPlayer ->
                    val callback = AudioPlayerDelegate(
                        onFinished = { finishPlayback(PlaybackStatus.Ended) },
                        onError = { error -> failPlayback(error) },
                    )
                    delegate = callback
                    player = audioPlayer
                    audioPlayer.delegate = callback
                    audioPlayer.enableRate = true
                    audioPlayer.rate = playbackSpeed
                    _playbackState.update {
                        it.copy(durationMs = (audioPlayer.duration * 1_000).finiteLongOrZero())
                    }
                    check(audioPlayer.prepareToPlay() && audioPlayer.play()) {
                        "AVAudioPlayer failed to start playback"
                    }
                    _playbackState.update { it.copy(status = PlaybackStatus.Playing) }
                    startPositionUpdates()
                }
            }.onFailure(::failPlayback)
        }
    }

    private fun finishPlayback(status: PlaybackStatus) {
        stopPositionUpdates()
        val currentState = _playbackState.value
        _playbackState.value = currentState.copy(
            status = status,
            positionMs = if (status == PlaybackStatus.Ended) currentState.durationMs else currentState.positionMs,
        )
        val completed = continuation
        continuation = null
        disposeAudioResources()
        completed?.takeIf { it.isActive }?.resume(Unit)
    }

    private fun failPlayback(error: Throwable) {
        stopPositionUpdates()
        _playbackState.update {
            it.copy(status = PlaybackStatus.Error, errorMessage = error.message ?: "Audio playback failed")
        }
        val failed = continuation
        continuation = null
        disposeAudioResources()
        failed?.takeIf { it.isActive }?.resumeWithException(error)
    }

    private fun disposeCurrentPlayer() {
        stopPositionUpdates()
        val cancelled = continuation
        continuation = null
        disposeAudioResources()
        cancelled?.takeIf { it.isActive }?.cancel()
    }

    private fun disposeAudioResources() {
        player?.stop()
        player?.delegate = null
        player = null
        delegate = null
    }

    private fun startPositionUpdates() {
        if (positionJob?.isActive == true) return
        positionJob = scope.launch {
            while (true) {
                player?.let(::updatePosition)
                delay(100)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionJob?.cancel()
        positionJob = null
    }

    private fun updatePosition(audioPlayer: AVAudioPlayer) {
        _playbackState.update {
            it.copy(
                positionMs = (audioPlayer.currentTime * 1_000).finiteLongOrZero(),
                durationMs = (audioPlayer.duration * 1_000).finiteLongOrZero(),
            )
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private class AudioPlayerDelegate(
    private val onFinished: () -> Unit,
    private val onError: (Throwable) -> Unit,
) : NSObject(), AVAudioPlayerDelegateProtocol {
    override fun audioPlayerDidFinishPlaying(player: AVAudioPlayer, successfully: Boolean) {
        if (successfully) onFinished() else onError(IllegalStateException("AVAudioPlayer did not finish successfully"))
    }

    override fun audioPlayerDecodeErrorDidOccur(player: AVAudioPlayer, error: NSError?) {
        onError(IllegalStateException(error?.localizedDescription ?: "AVAudioPlayer decode failed"))
    }
}

private fun Double.finiteLongOrZero(): Long = if (isFinite() && this >= 0.0) toLong() else 0L
