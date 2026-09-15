package me.rerere.tts.provider.providers

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.rerere.tts.provider.TTSProviderSetting
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import java.util.Locale
import java.util.concurrent.CountDownLatch
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WindowsSystemSpeechSynthesizerTest {
    @Test
    fun unicodeAndScriptLikeTextRemainLiteralSsmlText() {
        val text = "你好 <voice name=\"other\"> & 'quote' ${'$'}(exit 7) `command`\n🙂"
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val fragment = windowsSpeechMarkup(TTSProviderSetting.SystemTTS(speechRate = 1.5f, pitch = 0.5f), text)
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(fragment.byteInputStream())
            assertEquals(text, document.documentElement.textContent)
            assertEquals("150.0%", document.documentElement.getAttribute("rate"))
            assertEquals("-50.0%", document.documentElement.getAttribute("pitch"))
            assertEquals(1, document.getElementsByTagName("*").length)
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun powershellUsesFixedCodeAndSeparateDataFilesAndDeletesBoth() = runBlocking {
        lateinit var input: Path
        lateinit var output: Path
        val text = "你好 ${'$'}(exit 7) & <literal>"
        val audio = byteArrayOf(1, 2, 3)
        val synthesizer = WindowsSystemSpeechSynthesizer { builder ->
            input = Path.of(builder.environment().getValue("RIKKAHUB_TTS_INPUT"))
            output = Path.of(builder.environment().getValue("RIKKAHUB_TTS_OUTPUT"))
            assertEquals(windowsSpeechMarkup(TTSProviderSetting.SystemTTS(), text), Files.readString(input))
            val script = String(Base64.getDecoder().decode(builder.command().last()), Charsets.UTF_16LE)
            assertFalse(script.contains(text))
            assertFalse(script.contains(input.toString()))
            assertTrue(script.contains("SpeakSsml"))
            assertTrue(script.contains("SetOutputToWaveFile"))
            assertTrue(builder.command().contains("-NoProfile"))
            Files.write(output, audio)
            TestProcess(0)
        }
        assertContentEquals(audio, synthesizer.synthesize(TTSProviderSetting.SystemTTS(), text))
        assertFalse(Files.exists(input.parent))
        assertFalse(Files.exists(output))
    }

    @Test
    fun processFailurePropagatesAndRemovesTemporaryFiles() = runBlocking {
        lateinit var directory: Path
        val synthesizer = WindowsSystemSpeechSynthesizer { builder ->
            directory = Path.of(builder.environment().getValue("RIKKAHUB_TTS_INPUT")).parent
            TestProcess(5)
        }
        assertFailsWith<IllegalStateException> { synthesizer.synthesize(TTSProviderSetting.SystemTTS(), "test") }
        assertFalse(Files.exists(directory))
    }

    @Test
    fun cancellationTerminatesPowerShellBeforeRemovingItsFiles() = runBlocking {
        val started = CompletableDeferred<Path>()
        val process = TestProcess(null)
        val synthesizer = WindowsSystemSpeechSynthesizer { builder ->
            started.complete(Path.of(builder.environment().getValue("RIKKAHUB_TTS_INPUT")).parent)
            process
        }
        val synthesis = async { synthesizer.synthesize(TTSProviderSetting.SystemTTS(), "test") }
        val directory = withTimeout(3_000) { started.await() }
        withTimeout(3_000) { synthesis.cancelAndJoin() }
        assertTrue(process.destroyed)
        assertFalse(Files.exists(directory))
    }

    private class TestProcess(private var result: Int?) : Process() {
        private val finished = CountDownLatch(if (result == null) 1 else 0)
        var destroyed = false
            private set
        override fun getOutputStream() = ByteArrayOutputStream()
        override fun getInputStream() = ByteArrayInputStream(byteArrayOf())
        override fun getErrorStream() = ByteArrayInputStream(byteArrayOf())
        override fun waitFor(): Int { finished.await(); return result!! }
        override fun exitValue(): Int = result ?: throw IllegalThreadStateException()
        override fun destroy() { destroyed = true; result = 1; finished.countDown() }
        override fun destroyForcibly(): Process { destroy(); return this }
        override fun isAlive(): Boolean = result == null
    }
}
