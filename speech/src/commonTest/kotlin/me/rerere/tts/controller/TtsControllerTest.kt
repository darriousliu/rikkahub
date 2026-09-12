@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package me.rerere.tts.controller

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.rerere.tts.model.AudioChunk
import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.PlaybackState
import me.rerere.tts.model.PlaybackStatus
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.model.TTSResponse
import me.rerere.tts.provider.TTSManager
import me.rerere.tts.provider.TTSProvider
import me.rerere.tts.provider.TTSProviderSetting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TtsControllerTest {
    @Test
    fun keepsProviderAndBlankTextGuards() = runTest {
        val f = Fixture(this)
        try {
            f.controller.setProvider(null)
            f.controller.speak(" ")
            assertEquals(null, f.controller.error.value)
            f.controller.speak("A")
            assertEquals("No TTS provider selected", f.controller.error.value)
            assertFalse(f.controller.isAvailable.value)
            f.controller.setProvider(TTSProviderSetting.SystemTTS())
            f.controller.speak("A")
            runCurrent()
            assertTrue(f.controller.isAvailable.value)
            assertEquals(null, f.controller.error.value)
            assertEquals(listOf("A"), f.audio.played)
        } finally { f.close() }
    }

    @Test
    fun keepsOriginalPrefetchWindowsAndPlaysEveryChunkOnceInOrder() = runTest {
        val f = Fixture(this)
        try {
            val chunks = listOf("A", "B", "C", "D", "E", "F", "G", "H", "I", "J")
            f.controller.speak(chunks.joinToString("\n\n"))
            runCurrent()
            // speak and the first worker iteration each prefetch a window of four.
            assertEquals(chunks.take(8).toSet(), f.provider.requests.toSet())
            assertEquals(listOf("A"), f.audio.played)
            for (index in chunks.indices) {
                assertEquals(index + 1, f.controller.currentChunk.value)
                // The tag exposes the remaining queue including the active chunk.
                assertEquals(chunks.size - index, f.controller.totalChunks.value)
                f.audio.finish()
                advanceTimeBy(121)
                runCurrent()
            }
            assertEquals(chunks, f.audio.played)
            assertEquals(chunks.associateWith { 1 }, f.provider.requests.groupingBy { it }.eachCount())
            assertFalse(f.controller.isSpeaking.value)
            assertEquals(PlaybackStatus.Ended, f.controller.playbackState.value.status)
        } finally { f.close() }
    }

    @Test
    fun appendsSkipsAndPausesWithoutInterruptingTheCurrentChunk() = runTest {
        val f = Fixture(this)
        try {
            f.controller.speak("A\n\nB\n\nC")
            runCurrent()
            f.controller.speak("D\n\nE", flush = false)
            f.controller.skipNext()
            f.controller.pause()
            f.controller.setSpeed(1.5f)
            f.controller.fastForward(2500)
            assertEquals(1, f.audio.pauses)
            assertEquals(1.5f, f.audio.playbackSpeed)
            assertEquals(2500L, f.audio.seek)
            f.audio.finish()
            advanceTimeBy(1000)
            runCurrent()
            assertEquals(listOf("A"), f.audio.played)
            f.controller.resume()
            advanceTimeBy(81)
            runCurrent()
            assertEquals(1, f.audio.resumes)
            for (text in listOf("C", "D", "E")) {
                assertEquals(text, f.audio.played.last())
                f.audio.finish()
                advanceTimeBy(121)
                runCurrent()
            }
            assertEquals(listOf("A", "C", "D", "E"), f.audio.played)
            assertTrue(f.provider.requests.groupingBy { it }.eachCount().values.all { it == 1 })
        } finally { f.close() }
    }

    @Test
    fun stopAndFlushCancelPrefetchAndAllowFreshPlaybackOfTheSameText() = runTest {
        val f = Fixture(this)
        try {
            f.provider.hold = true
            f.controller.speak("A\n\nB\n\nC")
            runCurrent()
            assertEquals(3, f.provider.requests.size)
            f.controller.stop()
            runCurrent()
            assertEquals(listOf<String?>("Stopped", "Stopped", "Stopped"), f.provider.cancellations)
            assertTrue(f.audio.played.isEmpty())
            assertEquals(0, f.controller.totalChunks.value)
            assertFalse(f.controller.isSpeaking.value)

            f.controller.speak("D\n\nE")
            runCurrent()
            f.provider.hold = false
            f.controller.speak("A")
            runCurrent()
            assertEquals(listOf("Reset", "Reset"), f.provider.cancellations.takeLast(2))
            assertEquals(listOf("A"), f.audio.played)
            f.controller.speak("A")
            runCurrent()
            assertEquals(listOf("A", "A"), f.audio.played)
            assertEquals(3, f.provider.requests.count { it == "A" })
            f.controller.setProvider(null)
            runCurrent()
            assertFalse(f.controller.isAvailable.value)
            assertFalse(f.controller.isSpeaking.value)
        } finally { f.close() }
        assertEquals(1, f.audio.releases)
    }

    @Test
    fun keepsSynthesisAndPlaybackErrorsAndContinuesWithTheNextChunk() = runTest {
        val f = Fixture(this)
        try {
            f.provider.failOn = "bad synthesis"
            f.controller.speak("bad synthesis")
            runCurrent()
            assertEquals("TTS synthesis error", f.controller.error.value)
            assertTrue(f.audio.played.isEmpty())

            f.audio.failOn = "bad audio"
            f.controller.speak("bad audio\n\ngood")
            runCurrent()
            assertEquals("Audio playback error", f.controller.error.value)
            advanceTimeBy(121)
            runCurrent()
            assertEquals(listOf("bad audio", "good"), f.audio.played)
            f.audio.finish()
            runCurrent()
            assertFalse(f.controller.isSpeaking.value)
        } finally { f.close() }
    }

    private class Fixture(scope: TestScope) {
        val provider = Provider()
        val audio = Audio()
        private val client = HttpClient(MockEngine { error("Unexpected network request") })
        val controller = TtsController(
            TTSManager(client, provider), audio,
            StandardTestDispatcher(scope.testScheduler), StandardTestDispatcher(scope.testScheduler),
        ).apply { setProvider(TTSProviderSetting.SystemTTS()) }
        fun close() { controller.dispose(); client.close() }
    }

    private class Provider : TTSProvider<TTSProviderSetting.SystemTTS> {
        val requests = mutableListOf<String>()
        val cancellations = mutableListOf<String?>()
        var hold = false
        var failOn: String? = null
        override fun generateSpeech(providerSetting: TTSProviderSetting.SystemTTS, request: TTSRequest) = flow {
            requests += request.text
            try {
                if (hold) awaitCancellation()
                if (request.text == failOn) throw Exception()
                emit(AudioChunk(request.text.encodeToByteArray(), AudioFormat.MP3, isLast = true))
            } catch (e: CancellationException) {
                cancellations += e.message
                throw e
            }
        }
    }

    private class Audio : PlatformAudioPlayer {
        override val playbackState = MutableStateFlow(PlaybackState())
        val played = mutableListOf<String>()
        private var current: CompletableDeferred<Unit>? = null
        var failOn: String? = null
        var pauses = 0
        var resumes = 0
        var releases = 0
        var playbackSpeed = 1f
        var seek = 0L
        override suspend fun play(response: TTSResponse) {
            val text = response.audioData.decodeToString()
            played += text
            if (text == failOn) throw Exception()
            val pending = CompletableDeferred<Unit>()
            current = pending
            playbackState.value = PlaybackState(status = PlaybackStatus.Playing)
            try { pending.await() } finally { if (current === pending) current = null }
        }
        fun finish() { current?.complete(Unit); playbackState.value = PlaybackState(status = PlaybackStatus.Ended) }
        override fun pause() { pauses++; playbackState.value = PlaybackState(status = PlaybackStatus.Paused) }
        override fun resume() { resumes++; playbackState.value = PlaybackState(status = PlaybackStatus.Playing) }
        override fun stop() { current?.cancel(); playbackState.value = PlaybackState() }
        override fun clear() = Unit
        override fun release() { releases++ }
        override fun setSpeed(speed: Float) { playbackSpeed = speed }
        override fun seekBy(ms: Long) { seek += ms }
    }
}
