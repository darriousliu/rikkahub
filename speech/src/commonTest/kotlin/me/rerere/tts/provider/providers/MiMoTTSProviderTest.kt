package me.rerere.tts.provider.providers

import me.rerere.common.http.SseEvent
import me.rerere.tts.model.AudioFormat
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MiMoTTSProviderTest {
    @Test
    fun decode_audio_data_from_sse_chunk() {
        val expected = byteArrayOf(1, 2, 3, 4)
        val encoded = "AQIDBA=="
        val data = """{"choices":[{"delta":{"audio":{"data":"$encoded"}}}]}"""

        val actual = decodeMiMoAudioData(data)

        assertNotNull(actual)
        assertContentEquals(expected, actual)
    }

    @Test
    fun decode_unpadded_audio_data_from_sse_chunk() {
        val data = """{"choices":[{"delta":{"audio":{"data":"AQIDBA"}}}]}"""

        assertContentEquals(byteArrayOf(1, 2, 3, 4), decodeMiMoAudioData(data))
    }

    @Test
    fun ignore_sse_chunk_without_audio_data() {
        val data = """{"choices":[{"delta":{"content":"hello"}}]}"""
        assertNull(decodeMiMoAudioData(data))
    }

    @Test
    fun emits_single_terminal_chunk_on_done_and_closed() {
        val processor = MiMoSseProcessor(model = "mimo-v2-tts", voice = "mimo_default")
        val encoded = "CQgH"
        val audioData = """{"choices":[{"delta":{"audio":{"data":"$encoded"}}}]}"""

        val first = processor.process(SseEvent.Event(id = null, type = null, data = audioData))
        val done = processor.process(SseEvent.Event(id = null, type = null, data = "[DONE]"))
        val terminal = processor.process(SseEvent.Closed)

        assertNotNull(first)
        assertEquals(AudioFormat.PCM, first?.format)
        assertFalse(first?.isLast ?: true)
        assertNull(done)
        assertNotNull(terminal)
        assertTrue(terminal?.isLast ?: false)
    }

    @Test
    fun throws_when_stream_closed_without_audio() {
        val processor = MiMoSseProcessor(model = "mimo-v2-tts", voice = "mimo_default")

        var thrown: Throwable? = null
        try {
            processor.process(SseEvent.Event(id = null, type = null, data = "[DONE]"))
            processor.process(SseEvent.Closed)
        } catch (t: Throwable) {
            thrown = t
        }

        assertNotNull(thrown)
        assertTrue(thrown is IllegalStateException)
    }
}
