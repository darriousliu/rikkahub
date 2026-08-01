package me.rerere.tts.controller

import javafx.application.Platform
import javafx.beans.value.ChangeListener
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.PlaybackState
import me.rerere.tts.model.PlaybackStatus
import me.rerere.tts.model.TTSResponse
import java.nio.file.Files
import java.nio.file.Path
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class JvmAudioPlayer : PlatformAudioPlayer {
    private val _playbackState = MutableStateFlow(PlaybackState())
    override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var player: MediaPlayer? = null
    private var audioFile: Path? = null
    private var continuation: CancellableContinuation<Unit>? = null
    private var playbackSpeed = 1.0f

    override fun pause() = onJavaFxThread {
        player?.pause()
    }

    override fun resume() = onJavaFxThread {
        player?.play()
    }

    override fun stop() = onJavaFxThread {
        player?.stop()
        finishPlayback(PlaybackStatus.Idle)
    }

    override fun clear() = onJavaFxThread {
        disposeCurrentPlayer()
        _playbackState.value = PlaybackState(speed = playbackSpeed)
    }

    override fun release() = clear()

    override fun seekBy(ms: Long) = onJavaFxThread {
        player?.let { current ->
            current.seek(current.currentTime.add(javafx.util.Duration.millis(ms.toDouble())))
        }
    }

    override fun setSpeed(speed: Float) {
        playbackSpeed = speed
        _playbackState.update { it.copy(speed = speed) }
        onJavaFxThread {
            player?.rate = speed.toDouble()
        }
    }

    override suspend fun play(response: TTSResponse) {
        val file = withContext(Dispatchers.IO) {
            Files.createTempFile("rikkahub-tts-", response.format.fileExtension).also { path ->
                Files.write(path, audioBytesForPlayback(response))
            }
        }

        suspendCancellableCoroutine<Unit> { cont ->
            cont.invokeOnCancellation {
                runCatching {
                    onJavaFxThread {
                        if (continuation === cont) disposeCurrentPlayer()
                    }
                }
            }

            try {
                onJavaFxThread {
                    if (!cont.isActive) {
                        Files.deleteIfExists(file)
                        return@onJavaFxThread
                    }

                    disposeCurrentPlayer()
                    audioFile = file
                    continuation = cont
                    _playbackState.update {
                        it.copy(
                            status = PlaybackStatus.Buffering,
                            positionMs = 0L,
                            durationMs = response.duration?.times(1_000)?.toLong() ?: 0L,
                            errorMessage = null,
                        )
                    }

                    try {
                        player = MediaPlayer(Media(file.toUri().toString())).also { mediaPlayer ->
                            mediaPlayer.rate = playbackSpeed.toDouble()
                            mediaPlayer.setOnReady {
                                _playbackState.update {
                                    it.copy(durationMs = mediaPlayer.totalDuration.toMillis().finiteLongOrZero())
                                }
                                mediaPlayer.play()
                            }
                            mediaPlayer.setOnPlaying {
                                _playbackState.update { it.copy(status = PlaybackStatus.Playing) }
                            }
                            mediaPlayer.setOnPaused {
                                _playbackState.update { it.copy(status = PlaybackStatus.Paused) }
                            }
                            mediaPlayer.setOnEndOfMedia {
                                finishPlayback(PlaybackStatus.Ended)
                            }
                            mediaPlayer.setOnError {
                                failPlayback(mediaPlayer.error ?: IllegalStateException("Audio playback failed"))
                            }
                            mediaPlayer.currentTimeProperty().addListener(
                                ChangeListener { _, _, value ->
                                    _playbackState.update {
                                        it.copy(positionMs = value.toMillis().finiteLongOrZero())
                                    }
                                }
                            )
                        }
                    } catch (error: Throwable) {
                        failPlayback(error)
                    }
                }
            } catch (error: Throwable) {
                Files.deleteIfExists(file)
                if (cont.isActive) cont.resumeWithException(error)
            }
        }
    }

    private fun finishPlayback(status: PlaybackStatus) {
        _playbackState.update { state ->
            state.copy(
                status = status,
                positionMs = if (status == PlaybackStatus.Ended) state.durationMs else state.positionMs,
            )
        }
        val completed = continuation
        continuation = null
        disposeMediaResources()
        completed?.takeIf { it.isActive }?.resume(Unit)
    }

    private fun failPlayback(error: Throwable) {
        _playbackState.update {
            it.copy(status = PlaybackStatus.Error, errorMessage = error.message ?: "Audio playback failed")
        }
        continuation?.takeIf { it.isActive }?.resumeWithException(error)
        continuation = null
        disposeCurrentPlayer()
    }

    private fun disposeCurrentPlayer() {
        val cancelled = continuation
        continuation = null
        disposeMediaResources()
        cancelled?.takeIf { it.isActive }?.cancel()
    }

    private fun disposeMediaResources() {
        player?.dispose()
        player = null
        audioFile?.let { runCatching { Files.deleteIfExists(it) } }
        audioFile = null
    }
}

private val AudioFormat.fileExtension: String
    get() = when (this) {
        AudioFormat.MP3 -> ".mp3"
        AudioFormat.WAV, AudioFormat.PCM -> ".wav"
        AudioFormat.OGG -> ".ogg"
        AudioFormat.AAC -> ".aac"
        AudioFormat.OPUS -> ".opus"
    }

private fun Double.finiteLongOrZero(): Long = if (isFinite() && this >= 0.0) toLong() else 0L

private fun onJavaFxThread(block: () -> Unit) {
    JavaFxRuntime.ensureStarted()
    if (Platform.isFxApplicationThread()) block() else Platform.runLater(block)
}

private object JavaFxRuntime {
    @Volatile
    private var started = false

    fun ensureStarted() {
        if (started) return
        synchronized(this) {
            if (started) return
            try {
                Platform.startup {
                    Platform.setImplicitExit(false)
                }
            } catch (_: IllegalStateException) {
                Platform.runLater {
                    Platform.setImplicitExit(false)
                }
            }
            started = true
        }
    }
}
