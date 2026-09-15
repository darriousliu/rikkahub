package me.rerere.speech.utils

import me.rerere.tts.controller.audioBytesForPlayback
import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.TTSResponse
import javax.sound.sampled.AudioFormat as JavaAudioFormat
import javax.sound.sampled.AudioSystem
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame

class PcmAudioTest {
    private val pcm = ByteArray(8_192) { (it % 256).toByte() }

    @Test
    fun wavIsReadableAsTheOriginalPcmByAnIndependentDecoder() {
        listOf(16_000 to 1, 24_000 to 1, 44_100 to 2).forEach { (sampleRate, channels) ->
            assertWav(pcmToWav(pcm, sampleRate, channels), sampleRate, channels)
        }
    }

    @Test
    fun playbackKeepsDefaultAndExplicitSampleRates() {
        val response = TTSResponse(audioData = pcm, format = AudioFormat.PCM)
        assertWav(audioBytesForPlayback(response), sampleRate = 24_000)
        assertWav(audioBytesForPlayback(response.copy(sampleRate = 16_000)), sampleRate = 16_000)
    }

    @Test
    fun encodedPlaybackAudioIsPassedThrough() {
        AudioFormat.entries.filter { it != AudioFormat.PCM }.forEach { format ->
            assertSame(pcm, audioBytesForPlayback(TTSResponse(audioData = pcm, format = format)))
        }
    }

    private fun assertWav(wav: ByteArray, sampleRate: Int, channels: Int = 1) {
        AudioSystem.getAudioInputStream(wav.inputStream()).use { audio ->
            assertEquals(JavaAudioFormat.Encoding.PCM_SIGNED, audio.format.encoding)
            assertEquals(sampleRate.toFloat(), audio.format.sampleRate)
            assertEquals(channels, audio.format.channels)
            assertEquals(16, audio.format.sampleSizeInBits)
            assertEquals(channels * 2, audio.format.frameSize)
            assertFalse(audio.format.isBigEndian)
            assertEquals((pcm.size / (channels * 2)).toLong(), audio.frameLength)
            assertContentEquals(pcm, audio.readBytes())
        }
    }
}
