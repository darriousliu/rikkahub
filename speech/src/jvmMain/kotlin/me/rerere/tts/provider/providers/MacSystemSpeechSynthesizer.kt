package me.rerere.tts.provider.providers

import com.sun.jna.Callback
import com.sun.jna.CallbackReference
import com.sun.jna.Memory
import com.sun.jna.NativeLibrary
import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.tts.provider.TTSProviderSetting
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** Mirrors the iOS AVSpeechSynthesizer -> AVAudioFile path through the existing JVM JNA dependency. */
internal object MacSystemSpeechSynthesizer {
    suspend fun synthesize(setting: TTSProviderSetting.SystemTTS, text: String): ByteArray =
        withContext(Dispatchers.IO) {
            val path = Files.createTempFile("rikkahub-system-tts-", ".wav")
            try {
                MacSpeechSession(path).use { session ->
                    session.start(setting, text)
                    session.completed.await()
                }
                Files.readAllBytes(path)
            } finally {
                Files.deleteIfExists(path)
            }
        }
}

private class MacSpeechSession(private val path: Path) : AutoCloseable {
    val completed = CompletableDeferred<Unit>()
    private val id = SpeechObjC.nextId.incrementAndGet()
    private val synthesizer = SpeechObjC.autoreleasePool {
        SpeechObjC.pointer(SpeechObjC.type("AVSpeechSynthesizer"), "new")!!
    }
    private var outputFile: Pointer? = null

    fun start(setting: TTSProviderSetting.SystemTTS, text: String) = SpeechObjC.autoreleasePool {
        val utterance = SpeechObjC.pointer(
            SpeechObjC.type("AVSpeechUtterance"), "speechUtteranceWithString:", SpeechObjC.string(text),
        )!!
        val rate = (SpeechObjC.constant("AVSpeechUtteranceDefaultSpeechRate") * setting.speechRate).coerceIn(
            SpeechObjC.constant("AVSpeechUtteranceMinimumSpeechRate"),
            SpeechObjC.constant("AVSpeechUtteranceMaximumSpeechRate"),
        )
        SpeechObjC.void(utterance, "setRate:", rate)
        SpeechObjC.void(utterance, "setPitchMultiplier:", setting.pitch.coerceIn(0.5f, 2.0f))
        SpeechObjC.sessions[id] = this
        SpeechObjC.withBufferBlock(id) { block ->
            SpeechObjC.void(synthesizer, "writeUtterance:toBufferCallback:", utterance, block)
        }
    }

    // Native buffers only live during their callback. Write before returning; coordinate with cancellation/close.
    @Synchronized
    fun accept(buffer: Pointer) {
        if (completed.isCompleted) return
        try {
            check(SpeechObjC.int(buffer, "isKindOfClass:", SpeechObjC.type("AVAudioPCMBuffer")) and 0xff != 0) {
                "AVSpeechSynthesizer returned a non-PCM buffer"
            }
            if (SpeechObjC.int(buffer, "frameLength") == 0) {
                check(outputFile != null) { "AVSpeechSynthesizer produced no audio file" }
                completed.complete(Unit)
                return
            }
            val error = PointerByReference()
            val file = outputFile ?: run {
                val url = SpeechObjC.pointer(
                    SpeechObjC.type("NSURL"), "fileURLWithPath:", SpeechObjC.string(path.toString()),
                )
                val format = SpeechObjC.pointer(buffer, "format")
                SpeechObjC.pointer(
                    SpeechObjC.pointer(SpeechObjC.type("AVAudioFile"), "alloc"),
                    "initForWriting:settings:error:", url, SpeechObjC.pointer(format, "settings"), error,
                ) ?: error(SpeechObjC.errorDescription(error.value) ?: "Could not create speech audio file")
            }.also { outputFile = it }
            check(SpeechObjC.int(file, "writeFromBuffer:error:", buffer, error) and 0xff != 0) {
                SpeechObjC.errorDescription(error.value) ?: "Failed to write speech audio buffer"
            }
        } catch (error: Throwable) {
            completed.completeExceptionally(error)
        }
    }

    override fun close() = SpeechObjC.autoreleasePool {
        // The callback is static and copied blocks only capture an ID, so late native callbacks are harmless.
        SpeechObjC.sessions.remove(id)
        synchronized(this) {
            completed.cancel()
            outputFile?.let {
                // Releasing our owned file also closes it; explicit close requires macOS 15.
                SpeechObjC.void(it, "release")
            }
            outputFile = null
        }
        SpeechObjC.int(synthesizer, "stopSpeakingAtBoundary:", NativeLong(0))
        SpeechObjC.void(synthesizer, "release")
    }
}

private object SpeechObjC {
    private val framework = NativeLibrary.getInstance("/System/Library/Frameworks/AVFAudio.framework/AVFAudio")
    private val runtime = NativeLibrary.getInstance("objc")
    private val system = NativeLibrary.getInstance("System")
    private val send = runtime.getFunction("objc_msgSend")
    private val selectors = ConcurrentHashMap<String, Pointer>()
    val nextId = AtomicLong()
    val sessions = ConcurrentHashMap<Long, MacSpeechSession>()

    private interface BufferCallback : Callback {
        fun invoke(block: Pointer, buffer: Pointer)
    }

    private val bufferCallback = object : BufferCallback {
        override fun invoke(block: Pointer, buffer: Pointer) {
            sessions[block.getLong(32)]?.accept(buffer)
        }
    }

    // Apple's 64-bit Blocks ABI: isa, flags/reserved, invoke, descriptor, then the captured session ID.
    // AVSpeechSynthesizer copies the stack block; descriptor and callback must outlive every copy.
    private val blockSignature = Memory(11).apply { setString(0, "v16@?0@8") }
    private val blockDescriptor = Memory(24).apply {
        clear()
        setLong(8, 40)
        setPointer(16, blockSignature)
    }

    fun withBufferBlock(id: Long, action: (Pointer) -> Unit) = Memory(40).use { block ->
        block.clear()
        block.setPointer(0, system.getGlobalVariableAddress("_NSConcreteStackBlock"))
        block.setInt(8, 1 shl 30)
        block.setPointer(16, CallbackReference.getFunctionPointer(bufferCallback))
        block.setPointer(24, blockDescriptor)
        block.setLong(32, id)
        action(block)
    }

    fun <T> autoreleasePool(action: () -> T): T {
        val pool = pointer(type("NSAutoreleasePool"), "new")!!
        return try { action() } finally { void(pool, "drain") }
    }

    fun constant(name: String): Float = framework.getGlobalVariableAddress(name).getFloat(0)
    fun type(name: String): Pointer = runtime.getFunction("objc_getClass").invokePointer(arrayOf(name))
    private fun selector(name: String): Pointer = selectors.computeIfAbsent(name) {
        runtime.getFunction("sel_registerName").invokePointer(arrayOf(it))
    }
    fun pointer(target: Pointer?, name: String, vararg args: Any?): Pointer? =
        send.invokePointer(arrayOf(target, selector(name), *args))
    fun void(target: Pointer?, name: String, vararg args: Any?) =
        send.invokeVoid(arrayOf(target, selector(name), *args))
    fun int(target: Pointer, name: String, vararg args: Any?): Int =
        send.invokeInt(arrayOf(target, selector(name), *args))
    fun string(value: String): Pointer = pointer(type("NSString"), "stringWithUTF8String:", value)!!
    fun errorDescription(error: Pointer?): String? = error?.let {
        pointer(pointer(it, "localizedDescription"), "UTF8String")?.getString(0, "UTF-8")
    }
}
