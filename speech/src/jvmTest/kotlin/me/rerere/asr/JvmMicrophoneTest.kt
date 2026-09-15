package me.rerere.asr

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

class JvmMicrophoneTest {
    @Test
    fun releasedRecorderCannotReopenOrSpinOnEmptyReads() = runTest {
        val recorder = JvmMicrophone().createRecorder(16000, 4096)
        recorder.stop()
        recorder.release()
        assertFailsWith<CancellationException> { recorder.startRecording() }
        // A zero return here would keep the websocket controller's active read loop spinning after closure.
        assertFailsWith<CancellationException> { recorder.read(ByteArray(4096), 0, 4096) }
        recorder.release()
    }
}
