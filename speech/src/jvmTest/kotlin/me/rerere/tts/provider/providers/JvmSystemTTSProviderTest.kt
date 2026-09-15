package me.rerere.tts.provider.providers

import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.provider.TTSProviderSetting
import org.junit.Assume.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import javax.sound.sampled.AudioSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JvmSystemTTSProviderTest {
    @Test
    fun availabilityIsLimitedToWindowsAndMac() = runBlocking {
        assertTrue(JvmSystemTTSProvider("Windows 11").isAvailable)
        assertTrue(JvmSystemTTSProvider("Mac OS X").isAvailable)
        val linux = JvmSystemTTSProvider("Linux")
        assertFalse(linux.isAvailable)
        assertFalse(JvmSystemTTSProvider("Darwin").isAvailable)
        assertFailsWith<UnsupportedOperationException> {
            linux.generateSpeech(TTSProviderSetting.SystemTTS(), TTSRequest("hello")).single()
        }
        Unit
    }

    @Test
    fun installedSystemVoiceProducesPlayableAudioAndCleansUp() = runBlocking {
        assumeTrue(JvmSystemTTSProvider().isAvailable)
        val before = temporarySpeechFiles()
        val setting = TTSProviderSetting.SystemTTS(speechRate = 1.2f, pitch = 1.1f)
        val chunk = withTimeout(30_000) {
            JvmSystemTTSProvider().generateSpeech(setting, TTSRequest("Hello, this is a system speech test.")).single()
        }
        assertEquals(AudioFormat.WAV, chunk.format)
        assertTrue(chunk.isLast)
        assertEquals("system", chunk.metadata["provider"])
        assertEquals("1.2", chunk.metadata["speechRate"])
        assertEquals("1.1", chunk.metadata["pitch"])
        assertTrue(audioFrames(chunk.data) > 1_000)
        assertEquals(before, temporarySpeechFiles())
    }

    @Test
    fun nativePrefetchRequestsRemainIndependentAndSpeechRateChangesDuration() = runBlocking {
        assumeTrue(JvmSystemTTSProvider().isAvailable)
        val request = TTSRequest("One two three four five six seven eight nine ten. This is a longer speech rate test.")
        val provider = JvmSystemTTSProvider()
        withTimeout(45_000) {
            val normal = async { provider.generateSpeech(TTSProviderSetting.SystemTTS(), request).single() }
            val faster = async {
                provider.generateSpeech(TTSProviderSetting.SystemTTS(speechRate = 1.8f), request).single()
            }
            assertTrue(audioFrames(faster.await().data) < audioFrames(normal.await().data) * 0.9)
        }
    }

    @Test
    fun cancellationReleasesNativeSynthesisAndTemporaryAudio() = runBlocking {
        assumeTrue(JvmSystemTTSProvider().isAvailable)
        val before = temporarySpeechFiles()
        val synthesis = async {
            JvmSystemTTSProvider().generateSpeech(
                TTSProviderSetting.SystemTTS(), TTSRequest("A sentence for cancellation testing. ".repeat(500)),
            ).single()
        }
        delay(150)
        withTimeout(5_000) { synthesis.cancelAndJoin() }
        assertTrue(synthesis.isCancelled)
        assertEquals(before, temporarySpeechFiles())
        // A cancelled request must not break the next request or receive its callbacks.
        val next = withTimeout(30_000) {
            JvmSystemTTSProvider().generateSpeech(TTSProviderSetting.SystemTTS(), TTSRequest("Hello again.")).single()
        }
        assertTrue(audioFrames(next.data) > 1_000)
    }

    private fun audioFrames(bytes: ByteArray): Long = AudioSystem.getAudioInputStream(bytes.inputStream()).use {
        assertTrue(it.format.sampleRate > 0)
        assertTrue(it.readAllBytes().any { byte -> byte.toInt() != 0 }, "Synthesized audio must contain samples")
        it.frameLength
    }

    private fun temporarySpeechFiles(): Set<Path> = Files.list(Path.of(System.getProperty("java.io.tmpdir"))).use {
        it.filter { path -> path.fileName.toString().startsWith("rikkahub-system-tts-") }.toList().toSet()
    }
}
