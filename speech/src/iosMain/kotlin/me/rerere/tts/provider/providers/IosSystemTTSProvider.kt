package me.rerere.tts.provider.providers

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import me.rerere.tts.model.AudioChunk
import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.provider.TTSProvider
import me.rerere.tts.provider.TTSProviderSetting
import me.rerere.tts.toByteArray
import platform.AVFAudio.AVAudioFile
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVSpeechBoundary
import platform.AVFAudio.AVSpeechSynthesizer
import platform.AVFAudio.AVSpeechUtterance
import platform.AVFAudio.AVSpeechUtteranceDefaultSpeechRate
import platform.AVFAudio.AVSpeechUtteranceMaximumSpeechRate
import platform.AVFAudio.AVSpeechUtteranceMinimumSpeechRate
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfFile
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.uuid.Uuid

@OptIn(ExperimentalForeignApi::class, ExperimentalAtomicApi::class)
class IosSystemTTSProvider : TTSProvider<TTSProviderSetting.SystemTTS> {
    val isAvailable: Boolean = true

    override fun generateSpeech(
        providerSetting: TTSProviderSetting.SystemTTS,
        request: TTSRequest,
    ): Flow<AudioChunk> = flow {
        val audioData = synthesize(providerSetting, request.text)
        emit(
            AudioChunk(
                data = audioData,
                format = AudioFormat.WAV,
                isLast = true,
                metadata = mapOf(
                    "provider" to "system",
                    "speechRate" to providerSetting.speechRate.toString(),
                    "pitch" to providerSetting.pitch.toString(),
                ),
            )
        )
    }

    private suspend fun synthesize(setting: TTSProviderSetting.SystemTTS, text: String): ByteArray =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val synthesizer = AVSpeechSynthesizer()
                val utterance = AVSpeechUtterance(string = text).apply {
                    rate = (AVSpeechUtteranceDefaultSpeechRate * setting.speechRate).coerceIn(
                        AVSpeechUtteranceMinimumSpeechRate,
                        AVSpeechUtteranceMaximumSpeechRate,
                    )
                    pitchMultiplier = setting.pitch.coerceIn(0.5f, 2.0f)
                }
                val path = "${NSTemporaryDirectory()}rikkahub-system-tts-${Uuid.random()}.wav"
                val url = NSURL.fileURLWithPath(path)
                val completed = AtomicBoolean(false)
                var outputFile: AVAudioFile? = null

                fun cleanup() {
                    outputFile?.close()
                    outputFile = null
                    NSFileManager.defaultManager.removeItemAtPath(path, error = null)
                }

                fun fail(error: Throwable) {
                    if (!completed.compareAndSet(expectedValue = false, newValue = true)) return
                    cleanup()
                    if (continuation.isActive) continuation.resumeWithException(error)
                }

                continuation.invokeOnCancellation {
                    if (completed.compareAndSet(expectedValue = false, newValue = true)) {
                        synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
                        cleanup()
                    }
                }

                synthesizer.writeUtterance(utterance) { buffer ->
                    if (completed.load()) return@writeUtterance
                    val pcmBuffer = buffer as? AVAudioPCMBuffer
                    if (pcmBuffer == null) {
                        fail(IllegalStateException("AVSpeechSynthesizer returned a non-PCM buffer"))
                        return@writeUtterance
                    }

                    if (pcmBuffer.frameLength == 0U) {
                        outputFile?.close()
                        outputFile = null
                        val data = NSData.dataWithContentsOfFile(path)
                        if (data == null) {
                            fail(IllegalStateException("AVSpeechSynthesizer produced no audio file"))
                        } else if (completed.compareAndSet(expectedValue = false, newValue = true)) {
                            val bytes = data.toByteArray()
                            cleanup()
                            if (continuation.isActive) continuation.resume(bytes)
                        }
                        return@writeUtterance
                    }

                    runCatching {
                        val file = outputFile ?: AVAudioFile(
                            forWriting = url,
                            settings = pcmBuffer.format.settings,
                            error = null,
                        ).also { outputFile = it }
                        check(file.writeFromBuffer(pcmBuffer, error = null)) {
                            "Failed to write AVSpeechSynthesizer audio buffer"
                        }
                    }.onFailure(::fail)
                }
            }
        }
}
