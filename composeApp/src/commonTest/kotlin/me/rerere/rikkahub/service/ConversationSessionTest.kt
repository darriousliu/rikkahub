package me.rerere.rikkahub.service

import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.rerere.rikkahub.data.model.Conversation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class ConversationSessionTest {
    @Test
    fun `only the last reference release starts the five second idle timeout`() = runTest {
        val idle = mutableListOf<Uuid>()
        val session = session(idle)
        assertEquals(1, session.acquire())
        assertEquals(2, session.acquire())
        assertEquals(1, session.release())
        advanceTimeBy(10_000)
        runCurrent()
        assertTrue(session.isInUse)
        assertTrue(idle.isEmpty())

        assertEquals(0, session.release())
        runCurrent()
        advanceTimeBy(4_999)
        runCurrent()
        assertTrue(idle.isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(session.id), idle)
        assertFalse(session.isInUse)
    }

    @Test
    fun `acquiring again cancels the old timeout and the next release starts a full new delay`() = runTest {
        val idle = mutableListOf<Uuid>()
        val session = session(idle)
        session.acquire()
        session.release()
        runCurrent()
        advanceTimeBy(4_000)
        session.acquire()
        advanceTimeBy(2_000)
        runCurrent()
        assertTrue(idle.isEmpty())

        session.release()
        runCurrent()
        advanceTimeBy(4_999)
        runCurrent()
        assertTrue(idle.isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(session.id), idle)
    }

    @Test
    fun `reference scopes preserve results and release nested references on failure`() = runTest {
        val idle = mutableListOf<Uuid>()
        val session = session(idle)
        assertEquals("nested", session.withRef {
            assertTrue(session.isInUse)
            session.withRef { "nested" }
        })
        assertFalse(session.isInUse)
        assertEquals("suspended", session.withRefSuspend {
            delay(10)
            assertTrue(session.isInUse)
            "suspended"
        })
        val failure = IllegalStateException("Block failed")
        assertSame(failure, assertFailsWith<IllegalStateException> {
            session.withRef { session.withRef { throw failure } }
        })
        assertSame(failure, assertFailsWith<IllegalStateException> {
            session.withRefSuspend { delay(1); throw failure }
        })
        assertFalse(session.isInUse)
        assertEquals(1, session.acquire())
        assertEquals(0, session.release())
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(listOf(session.id), idle)
    }

    @Test
    fun `cancelling a suspended reference block releases it before idle notification`() = runTest {
        val idle = mutableListOf<Uuid>()
        val session = session(idle)
        val work = backgroundScope.launch { session.withRefSuspend { awaitCancellation() } }
        runCurrent()
        assertTrue(session.isInUse)
        advanceTimeBy(5_000)
        runCurrent()
        assertTrue(idle.isEmpty())

        work.cancel()
        runCurrent()
        assertTrue(work.isCompleted)
        assertFalse(session.isInUse)
        advanceTimeBy(4_999)
        runCurrent()
        assertTrue(idle.isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(session.id), idle)
    }

    @Test
    fun `active generation prevents idle notification and completion starts a new delay`() = runTest {
        val idle = mutableListOf<Uuid>()
        val session = session(idle)
        val job = Job()
        session.setJob(job)
        assertSame(job, session.getJob())
        assertSame(job, session.generationJob.value)
        assertTrue(session.isGenerating)
        assertTrue(session.isInUse)
        session.acquire()
        session.release()
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()
        assertTrue(idle.isEmpty())

        job.complete()
        assertNull(session.getJob())
        assertNull(session.generationJob.value)
        assertFalse(session.isGenerating)
        runCurrent()
        advanceTimeBy(4_999)
        runCurrent()
        assertTrue(idle.isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(session.id), idle)
    }

    @Test
    fun `replacing or clearing a job cancels the previous job`() = runTest {
        val idle = mutableListOf<Uuid>()
        val session = session(idle)
        val first = Job()
        val second = Job()
        session.setJob(first)
        session.setJob(second)
        assertTrue(first.isCancelled)
        assertSame(second, session.getJob())
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()
        assertTrue(idle.isEmpty())

        session.setJob(null)
        assertTrue(second.isCancelled)
        assertNull(session.generationJob.value)
        assertFalse(session.isInUse)
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(listOf(session.id), idle)
    }

    @Test
    fun `completing generation with a held reference waits for its later release`() = runTest {
        val idle = mutableListOf<Uuid>()
        val session = session(idle)
        session.acquire()
        val job = Job()
        session.setJob(job)
        job.complete()
        advanceTimeBy(10_000)
        runCurrent()
        assertTrue(session.isInUse)
        assertTrue(idle.isEmpty())

        session.release()
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(listOf(session.id), idle)
    }

    @Test
    fun `cleanup cancels generation and idle checks while retaining state`() = runTest {
        val idle = mutableListOf<Uuid>()
        val session = session(idle)
        val state = session.state.value.copy(title = "Retain state")
        session.state.value = state
        session.processingStatus.value = "Retain processing status"
        val job = Job()
        session.setJob(job)
        session.acquire()
        session.release()
        runCurrent()

        session.cleanup()

        assertTrue(job.isCancelled)
        assertNull(session.getJob())
        assertFalse(session.isGenerating)
        assertSame(state, session.state.value)
        assertEquals("Retain processing status", session.processingStatus.value)
        advanceTimeBy(10_000)
        runCurrent()
        assertTrue(idle.isEmpty())
    }

    @Test
    fun `reference underflow retains the original counts without clamping`() = runTest {
        val idle = mutableListOf<Uuid>()
        val session = session(idle)
        assertEquals(-1, session.release())
        assertFalse(session.isInUse)
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(listOf(session.id), idle)
        assertEquals(0, session.acquire())
        assertFalse(session.isInUse)
        assertEquals(1, session.acquire())
        assertTrue(session.isInUse)
        assertEquals(0, session.release())
        session.cleanup()
    }

    private fun TestScope.session(idle: MutableList<Uuid>): ConversationSession {
        val id = Uuid.random()
        return ConversationSession(id, Conversation.ofId(id), backgroundScope) { idle += it }
    }
}
