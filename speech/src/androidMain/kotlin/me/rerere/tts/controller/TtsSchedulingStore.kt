package me.rerere.tts.controller

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

internal class TtsSchedulingStore<Chunk, Key, Value> {
    private val queue = ConcurrentLinkedQueue<Chunk>()
    private val allChunks = mutableListOf<Chunk>()
    private val cache = ConcurrentHashMap<Key, Deferred<Value>>()

    val queuedSize: Int
        get() = queue.size

    val chunkCount: Int
        get() = allChunks.size

    val isQueueEmpty: Boolean
        get() = queue.isEmpty()

    fun append(chunks: List<Chunk>) {
        allChunks.addAll(chunks)
        queue.addAll(chunks)
    }

    fun lastChunkOrNull(): Chunk? = allChunks.lastOrNull()

    fun chunkAtOrNull(index: Int): Chunk? = allChunks.getOrNull(index)

    fun poll(): Chunk? = queue.poll()

    fun skipNext(): Boolean = queue.poll() != null

    fun getOrPut(key: Key, create: () -> Deferred<Value>): Deferred<Value> =
        cache.computeIfAbsent(key) { create() }

    fun clearChunks() {
        queue.clear()
        allChunks.clear()
    }

    fun cancelAndClearCache(message: String) {
        cache.values.forEach { it.cancel(CancellationException(message)) }
        cache.clear()
    }
}
