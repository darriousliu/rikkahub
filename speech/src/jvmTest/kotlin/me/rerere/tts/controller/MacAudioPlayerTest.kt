package me.rerere.tts.controller

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.PlaybackStatus
import me.rerere.tts.model.TTSResponse
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.TimeSource

class MacAudioPlayerTest {
    @Test
    fun pauseRetainsPositionAndSelectedRateWithoutDelayingResume() = runBlocking {
        assumeTrue(System.getProperty("os.name").startsWith("Mac"))
        val player = MacAudioPlayer()
        try {
            val playback = async { player.play(silence(8_000)) }
            player.awaitStatus(PlaybackStatus.Playing)
            delay(300)
            repeat(2) {
                player.pause()
                player.awaitStatus(PlaybackStatus.Paused)
                val paused = player.playbackState.value.positionMs
                player.setSpeed(1.5f)
                delay(300)
                assertEquals(paused, player.playbackState.value.positionMs)
                assertEquals(PlaybackStatus.Paused, player.playbackState.value.status)

                val resumed = TimeSource.Monotonic.markNow()
                player.resume()
                player.awaitStatus(PlaybackStatus.Playing)
                withTimeout(400) { player.playbackState.first { it.positionMs > paused + 50 } }
                assertTrue(resumed.elapsedNow().inWholeMilliseconds < 400, "Playback should resume promptly")
                val position = player.playbackState.value.positionMs
                assertTrue(position - paused < 450, "Resume must not skip ahead")
                val start = TimeSource.Monotonic.markNow()
                delay(700)
                val elapsed = start.elapsedNow().inWholeMilliseconds
                val advanced = player.playbackState.value.positionMs - position
                assertTrue(
                    advanced in (elapsed * 1.2).toLong()..(elapsed * 1.8).toLong(), "Actual rate: $advanced/$elapsed"
                )
            }
            player.stop()
            withTimeout(1_000) { playback.await() }
            assertEquals(PlaybackStatus.Idle, player.playbackState.value.status)
        } finally {
            player.release()
        }
    }

    @Test
    fun completionAndDecodeFailureResumeTheAwaitingCaller() = runBlocking {
        assumeTrue(System.getProperty("os.name").startsWith("Mac"))
        val player = MacAudioPlayer()
        try {
            withTimeout(3_000) { player.play(silence(250)) }
            assertEquals(PlaybackStatus.Ended, player.playbackState.value.status)
            assertEquals(player.playbackState.value.durationMs, player.playbackState.value.positionMs)
            assertFailsWith<IllegalStateException> {
                withTimeout(3_000) { player.play(TTSResponse(byteArrayOf(1, 2, 3), AudioFormat.MP3)) }
            }
            assertEquals(PlaybackStatus.Error, player.playbackState.value.status)
        } finally {
            player.release()
        }
    }

    private suspend fun MacAudioPlayer.awaitStatus(status: PlaybackStatus) =
        withTimeout(3_000) { playbackState.first { it.status == status } }

    private fun silence(durationMs: Int) = TTSResponse(
        audioData = ByteArray(24_000 * 2 * durationMs / 1_000),
        format = AudioFormat.PCM,
        sampleRate = 24_000,
    )
}
