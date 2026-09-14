package me.rerere.tts.controller

import com.sun.jna.Callback
import com.sun.jna.CallbackReference
import com.sun.jna.Memory
import com.sun.jna.NativeLibrary
import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
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
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Uses the same AVAudioPlayer API as iOS, retaining prepared audio across pause/resume. */
class MacAudioPlayer : PlatformAudioPlayer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _playbackState = MutableStateFlow(PlaybackState())
    override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var player: MacAVAudioPlayer? = null
    private var continuation: CancellableContinuation<Unit>? = null
    private var positionJob: Job? = null
    private var playbackSpeed = 1.0f

    override fun pause() {
        scope.launch {
            player?.let { it.pause(); updatePosition(it) }
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
        scope.launch { finishPlayback(PlaybackStatus.Idle) }
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
        scope.launch { player?.rate = speed }
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
                scope.launch { if (continuation === cont) disposeCurrentPlayer() }
            }

            runCatching {
                val audioPlayer = MacAVAudioPlayer(audioBytesForPlayback(response)) { error ->
                    scope.launch {
                        if (continuation === cont) {
                            if (error == null) finishPlayback(PlaybackStatus.Ended) else failPlayback(error)
                        }
                    }
                }
                player = audioPlayer
                audioPlayer.rate = playbackSpeed
                _playbackState.update {
                    it.copy(durationMs = (audioPlayer.duration * 1_000).finiteLongOrZero())
                }
                check(audioPlayer.prepareToPlay() && audioPlayer.play()) {
                    "AVAudioPlayer failed to start playback"
                }
                _playbackState.update { it.copy(status = PlaybackStatus.Playing) }
                startPositionUpdates()
            }.onFailure(::failPlayback)
        }
    }

    private fun finishPlayback(status: PlaybackStatus) {
        stopPositionUpdates()
        _playbackState.update {
            it.copy(status = status, positionMs = if (status == PlaybackStatus.Ended) it.durationMs else it.positionMs)
        }
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
        player?.close()
        player = null
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

    private fun updatePosition(audioPlayer: MacAVAudioPlayer) {
        _playbackState.update {
            it.copy(
                positionMs = (audioPlayer.currentTime * 1_000).finiteLongOrZero(),
                durationMs = (audioPlayer.duration * 1_000).finiteLongOrZero(),
            )
        }
    }
}

private fun Double.finiteLongOrZero(): Long = if (isFinite() && this >= 0.0) toLong() else 0L

/** Only the Objective-C calls needed by AVAudioPlayer; no application state lives here. */
private class MacAVAudioPlayer(bytes: ByteArray, onCompletion: (Throwable?) -> Unit) : AutoCloseable {
    private val handle: Pointer
    private val delegate: Pointer

    init {
        val pool = ObjC.pointer(ObjC.type("NSAutoreleasePool"), "new")!!
        try {
            val error = PointerByReference()
            Memory(bytes.size.toLong()).use { memory ->
                memory.write(0, bytes, 0, bytes.size)
                val data = ObjC.pointer(
                    ObjC.type("NSData"), "dataWithBytes:length:", memory, NativeLong(bytes.size.toLong())
                )
                val allocated = ObjC.pointer(ObjC.type("AVAudioPlayer"), "alloc")
                handle = ObjC.pointer(allocated, "initWithData:error:", data, error)
                    ?: throw IllegalStateException(ObjC.errorDescription(error.value) ?: "AVAudioPlayer decode failed")
            }
            delegate = ObjC.pointer(ObjC.delegateClass, "new")!!
            ObjC.completions[Pointer.nativeValue(delegate)] = onCompletion
            ObjC.void(handle, "setDelegate:", delegate)
            ObjC.void(handle, "setEnableRate:", 1.toByte())
        } finally {
            ObjC.void(pool, "drain")
        }
    }

    var currentTime: Double
        get() = ObjC.double(handle, "currentTime")
        set(value) = ObjC.void(handle, "setCurrentTime:", value)
    val duration: Double get() = ObjC.double(handle, "duration")
    var rate: Float
        get() = ObjC.float(handle, "rate")
        set(value) = ObjC.void(handle, "setRate:", value)

    fun prepareToPlay(): Boolean = ObjC.bool(handle, "prepareToPlay")
    fun play(): Boolean = ObjC.bool(handle, "play")
    fun pause() = ObjC.void(handle, "pause")

    override fun close() {
        ObjC.void(handle, "stop")
        ObjC.void(handle, "setDelegate:", null)
        ObjC.completions.remove(Pointer.nativeValue(delegate))
        ObjC.void(delegate, "release")
        ObjC.void(handle, "release")
    }
}

private object ObjC {
    // Keep the framework and callback objects alive while native code can call them.
    private val avFoundation = NativeLibrary.getInstance(
        "/System/Library/Frameworks/AVFoundation.framework/AVFoundation"
    )
    private val runtime = NativeLibrary.getInstance("objc")
    private val send = runtime.getFunction("objc_msgSend")
    private val selectors = ConcurrentHashMap<String, Pointer>()
    val completions = ConcurrentHashMap<Long, (Throwable?) -> Unit>()

    private interface FinishedCallback : Callback {
        fun invoke(self: Pointer, selector: Pointer, player: Pointer, successful: Byte)
    }
    private interface ErrorCallback : Callback {
        fun invoke(self: Pointer, selector: Pointer, player: Pointer, error: Pointer?)
    }
    private val finished = object : FinishedCallback {
        override fun invoke(self: Pointer, selector: Pointer, player: Pointer, successful: Byte) {
            completions[Pointer.nativeValue(self)]?.invoke(
                if (successful.toInt() != 0) null
                else IllegalStateException("AVAudioPlayer did not finish successfully")
            )
        }
    }
    private val failed = object : ErrorCallback {
        override fun invoke(self: Pointer, selector: Pointer, player: Pointer, error: Pointer?) {
            completions[Pointer.nativeValue(self)]?.invoke(
                IllegalStateException(errorDescription(error) ?: "AVAudioPlayer decode failed")
            )
        }
    }
    val delegateClass: Pointer = runtime.getFunction("objc_allocateClassPair")
        .invokePointer(arrayOf(type("NSObject"), "RikkaHubAudioPlayerDelegate", NativeLong(0))).also { type ->
            check(type != null) { "Could not create AVAudioPlayer delegate" }
            check(runtime.getFunction("class_addMethod").invokeInt(arrayOf(
                type, selector("audioPlayerDidFinishPlaying:successfully:"),
                CallbackReference.getFunctionPointer(finished), "v@:@c",
            )) != 0)
            check(runtime.getFunction("class_addMethod").invokeInt(arrayOf(
                type, selector("audioPlayerDecodeErrorDidOccur:error:"),
                CallbackReference.getFunctionPointer(failed), "v@:@@",
            )) != 0)
            runtime.getFunction("objc_registerClassPair").invokeVoid(arrayOf(type))
        }

    fun type(name: String): Pointer = runtime.getFunction("objc_getClass").invokePointer(arrayOf(name))
    private fun selector(name: String): Pointer = selectors.computeIfAbsent(name) {
        runtime.getFunction("sel_registerName").invokePointer(arrayOf(it))
    }
    fun pointer(target: Pointer?, name: String, vararg args: Any?): Pointer? =
        send.invokePointer(arrayOf(target, selector(name), *args))
    fun void(target: Pointer?, name: String, vararg args: Any?) =
        send.invokeVoid(arrayOf(target, selector(name), *args))
    fun bool(target: Pointer, name: String): Boolean =
        send.invokeInt(arrayOf(target, selector(name))) and 0xff != 0
    fun double(target: Pointer, name: String): Double = send.invokeDouble(arrayOf(target, selector(name)))
    fun float(target: Pointer, name: String): Float = send.invokeFloat(arrayOf(target, selector(name)))
    fun errorDescription(error: Pointer?): String? = error?.let {
        pointer(pointer(it, "localizedDescription"), "UTF8String")?.getString(0, "UTF-8")
    }
}
