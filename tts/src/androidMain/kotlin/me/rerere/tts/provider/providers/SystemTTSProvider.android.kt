package me.rerere.tts.provider.providers

import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import me.rerere.common.PlatformContext
import me.rerere.common.android.appTempFolder
import me.rerere.common.utils.toFile
import me.rerere.tts.model.AudioChunk
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.provider.TTSProvider
import me.rerere.tts.provider.TTSProviderSetting
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Clock
import kotlin.uuid.Uuid

private const val TAG = "SystemTTSProvider"

actual class SystemTTSProvider :
    TTSProvider<TTSProviderSetting.SystemTTS> {
    actual override fun generateSpeech(
        context: PlatformContext,
        providerSetting: TTSProviderSetting.SystemTTS,
        request: TTSRequest
    ): Flow<AudioChunk> = flow {
        val audioData = suspendCancellableCoroutine<ByteArray> { continuation ->
            var tts: TextToSpeech? = null
            val listener = TextToSpeech.OnInitListener { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val ttsInstance = tts ?: error("TextToSpeech instance is null")

                    // Set language
                    val locale = Locale.getDefault()
                    val langResult = ttsInstance.setLanguage(locale)

                    if (langResult == TextToSpeech.LANG_MISSING_DATA ||
                        langResult == TextToSpeech.LANG_NOT_SUPPORTED
                    ) {
                        Logger.w(TAG) { "generateSpeech: Language $locale not supported" }
                    }

                    // Set speech parameters
                    ttsInstance.setSpeechRate(providerSetting.speechRate)
                    ttsInstance.setPitch(providerSetting.pitch)

                    // Create temporary file for audio output using temp directory like RikkaHubApp
                    val tempDir = context.appTempFolder.toFile()
                    val audioFile = File(tempDir, "tts_${Clock.System.now().toEpochMilliseconds()}.wav")

                    val utteranceId = Uuid.random().toString()

                    ttsInstance.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            Logger.i(TAG) { "onStart: TTS engine started!" }
                        }

                        override fun onDone(utteranceId: String?) {
                            try {
                                if (audioFile.exists()) {
                                    val audioData = audioFile.readBytes()
                                    audioFile.delete()

                                    if (continuation.isActive) continuation.resume(audioData)
                                } else {
                                    if (continuation.isActive) continuation.resumeWithException(
                                        Exception("Failed to generate audio file")
                                    )
                                }
                            } catch (e: Exception) {
                                if (continuation.isActive) continuation.resumeWithException(e)
                            } finally {
                                ttsInstance.shutdown()
                            }
                        }

                        override fun onError(utteranceId: String?) {
                            Logger.e(TAG) { "onError: TTS synthesis failed!" }
                            audioFile.delete()
                            if (continuation.isActive) continuation.resumeWithException(
                                Exception("TTS synthesis failed")
                            )
                            ttsInstance.shutdown()
                        }
                    })

                    val result = ttsInstance.synthesizeToFile(
                        request.text,
                        null,
                        audioFile,
                        utteranceId
                    )

                    if (result != TextToSpeech.SUCCESS) {
                        if (continuation.isActive) continuation.resumeWithException(
                            Exception("Failed to start TTS synthesis")
                        )
                        ttsInstance.shutdown()
                    }

                } else {
                    if (continuation.isActive) continuation.resumeWithException(
                        Exception("Failed to initialize TextToSpeech engine")
                    )
                }
            }
            tts = TextToSpeech(context, listener)

            continuation.invokeOnCancellation {
                tts?.shutdown()
            }
        }

        emit(
            AudioChunk(
                data = audioData,
                format = me.rerere.tts.model.AudioFormat.WAV,
                isLast = true,
                metadata = mapOf(
                    "provider" to "system",
                    "speechRate" to providerSetting.speechRate.toString(),
                    "pitch" to providerSetting.pitch.toString()
                )
            )
        )
    }
}
