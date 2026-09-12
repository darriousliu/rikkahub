@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package me.rerere.common.concurrent

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlin.concurrent.atomics.AtomicInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ConcurrentHashMapTest {
    @Test
    fun lookupReplacementAndConditionalRemovalKeepTheirOriginalResults() {
        val map = ConcurrentHashMap<String, String>()
        assertNull(map["server"])
        assertFalse(map.containsKey("server"))
        assertNull(map.putIfAbsent("server", "first"))
        assertEquals("first", map.putIfAbsent("server", "ignored"))
        assertEquals("first", map.put("server", "second"))
        assertTrue(map.containsKey("server"))
        assertEquals(1, map.size)
        assertFalse(map.remove("server", "first"))
        assertEquals("second", map["server"])
        assertTrue(map.remove("server", "second"))
        assertNull(map.remove("server"))
        map.put("server", "third")
        assertEquals("third", map.remove("server"))
        assertEquals(0, map.size)
    }

    @Test
    fun concurrentSessionCreationReusesExactlyOneInstance() = runBlocking {
        val map = ConcurrentHashMap<Int, Any>()
        val created = AtomicInt(0)
        val results = (0 until 64).map {
            async(Dispatchers.Default) {
                map.computeIfAbsent(1) { created.fetchAndAdd(1); Any() }
            }
        }.awaitAll()
        assertEquals(1, created.load())
        results.forEach { assertSame(results.first(), it) }
        assertEquals(listOf(results.first()), map.values.toList())
    }

    @Test
    fun concurrentWritersAndReadersDoNotLosePublishedEntries() = runBlocking {
        val map = ConcurrentHashMap<Int, Int>()
        (0 until 8).map { worker ->
            async(Dispatchers.Default) {
                repeat(128) {
                    val key = worker * 128 + it
                    assertNull(map.putIfAbsent(key, key))
                    assertEquals(key, map[key])
                    assertTrue(map.values.toList().all { value -> value in 0 until 1024 })
                }
            }
        }.awaitAll()
        assertEquals((0 until 1024).toSet(), map.keys.toSet())
        assertEquals((0 until 1024).toSet(), map.values.toList().toSet())
        (0 until 8).map { worker ->
            async(Dispatchers.Default) {
                repeat(128) {
                    val key = worker * 128 + it
                    assertTrue(map.remove(key, key))
                }
            }
        }.awaitAll()
        assertEquals(0, map.size)
    }

    @Test
    fun failedAndCancelledFactoriesRemainRetryable() {
        for (error in listOf(IllegalStateException("creation failed"), CancellationException("cancelled"))) {
            val map = ConcurrentHashMap<String, Any>()
            assertSame(error, runCatching { map.computeIfAbsent("session") { throw error } }.exceptionOrNull())
            assertFalse(map.containsKey("session"))
            assertEquals(0, map.size)
            val value = Any()
            assertSame(value, map.computeIfAbsent("session") { value })
            map.clear()
            assertNull(map["session"])
            assertTrue(map.values.toList().isEmpty())
            val replacement = Any()
            assertSame(replacement, map.computeIfAbsent("session") { replacement })
        }
    }

    @Test
    fun completionOfAnOldAuthorizationCannotRemoveItsReplacement() {
        val jobs = ConcurrentHashMap<String, Job>()
        val old = Job()
        val replacement = Job()
        jobs.put("server", old)
        old.invokeOnCompletion { jobs.remove("server", old) }
        jobs.put("server", replacement)
        old.complete()
        assertSame(replacement, jobs["server"])
        replacement.invokeOnCompletion { jobs.remove("server", replacement) }
        replacement.cancel()
        assertFalse(jobs.containsKey("server"))
    }

    @Test
    fun copiedValuesRemainTraversableWhileCleanupRemovesEntries() {
        val map = ConcurrentHashMap<Int, Job>()
        repeat(8) { key ->
            val job = Job()
            job.invokeOnCompletion { map.remove(key, job) }
            map.put(key, job)
        }
        val values = map.values.toList()
        values.forEach { it.cancel() }
        map.clear()
        assertEquals(8, values.size)
        assertTrue(values.all { it.isCancelled })
        assertTrue(map.values.toList().isEmpty())
    }

    @Test
    fun readingTheMapFromAFactorySeesOnlyPublishedValuesWithoutWaitingOnItself() {
        val map = ConcurrentHashMap<String, String>()
        map.put("existing", "ready")
        val value = map.computeIfAbsent("creating") { key ->
            assertEquals("creating", key)
            assertNull(map[key])
            assertFalse(map.containsKey(key))
            assertEquals(1, map.size)
            assertEquals(setOf("existing"), map.keys.toSet())
            assertEquals(listOf("ready"), map.values.toList())
            "created"
        }
        assertEquals("created", value)
        assertEquals(setOf("ready", "created"), map.values.toSet())
    }

    @Test
    fun concurrentRegistrationHasExactlyOneWinner() = runBlocking {
        val map = ConcurrentHashMap<String, Any>()
        val candidates = List(64) { Any() }
        val results = candidates.map { candidate ->
            async(Dispatchers.Default) { candidate to map.putIfAbsent("server", candidate) }
        }.awaitAll()
        val winner = results.single { it.second == null }.first
        assertSame(winner, map["server"])
        results.filter { it.first !== winner }.forEach { assertSame(winner, it.second) }
    }
}
