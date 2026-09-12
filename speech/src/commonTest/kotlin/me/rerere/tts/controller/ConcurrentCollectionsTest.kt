@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package me.rerere.tts.controller

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.concurrent.atomics.AtomicInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ConcurrentCollectionsTest {
    @Test
    fun queueKeepsFifoAndConcurrentProducersDoNotLoseItems() = runTest {
        val queue = ConcurrentLinkedQueue<Int>()
        queue.addAll(listOf(1, 2, 3))
        assertEquals(listOf(1, 2, 3, null), List(4) { queue.poll() })
        (0 until 8).map { producer ->
            async(Dispatchers.Default) { repeat(200) { queue.addAll(listOf(producer * 200 + it)) } }
        }.awaitAll()
        assertEquals(1600, queue.size)
        val values = (0 until 8).map {
            async(Dispatchers.Default) { buildList { while (true) add(queue.poll() ?: break) } }
        }.awaitAll().flatten()
        assertEquals((0 until 1600).toSet(), values.toSet())
        assertEquals(1600, values.size)
        assertTrue(queue.isEmpty())
        queue.addAll(listOf(4, 5))
        queue.clear()
        assertEquals(null, queue.poll())
    }

    @Test
    fun cacheCreatesOncePerKeyAndCanBeCleared() = runTest {
        val cache = ConcurrentHashMap<Int, Any>()
        val created = AtomicInt(0)
        val values = (0 until 64).map {
            async(Dispatchers.Default) {
                cache.computeIfAbsent(1) { created.fetchAndAdd(1); Any() }
            }
        }.awaitAll()
        assertEquals(1, created.load())
        assertTrue(values.all { it === values.first() })
        assertEquals(1, cache.values.size)
        cache.clear()
        assertTrue(cache.values.isEmpty())
        assertTrue(cache.computeIfAbsent(1) { Any() } !== values.first())
    }

    @Test
    fun failedCreationIsNotCachedAndDoesNotHoldTheContainerLock() {
        val cache = ConcurrentHashMap<Int, String>()
        assertFailsWith<IllegalStateException> { cache.computeIfAbsent(1) { error("create failed") } }
        assertEquals("retry", cache.computeIfAbsent(1) { "retry" })
        assertEquals(listOf("retry"), cache.values.toList())
    }
}
