package me.rerere.tts.controller

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
internal class TtsSchedulingStore<Chunk, Key, Value> {
    private val queue = AtomicReference<List<Chunk>>(emptyList())
    private val allChunks = AtomicReference<List<Chunk>>(emptyList())
    private val cache = AtomicReference<Map<Key, CacheSlot<Value>>>(emptyMap())

    val queuedSize: Int
        get() = queue.load().size

    val chunkCount: Int
        get() = allChunks.load().size

    val isQueueEmpty: Boolean
        get() = queue.load().isEmpty()

    fun append(chunks: List<Chunk>) {
        update(allChunks) { it + chunks }
        update(queue) { it + chunks }
    }

    fun lastChunkOrNull(): Chunk? = allChunks.load().lastOrNull()

    fun chunkAtOrNull(index: Int): Chunk? = allChunks.load().getOrNull(index)

    fun poll(): Chunk? {
        while (true) {
            val snapshot = queue.load()
            val first = snapshot.firstOrNull() ?: return null
            if (queue.compareAndSet(snapshot, snapshot.drop(1))) return first
        }
    }

    fun skipNext(): Boolean = poll() != null

    fun getOrPut(key: Key, create: () -> Deferred<Value>): Deferred<Value> {
        while (true) {
            val snapshot = cache.load()
            when (val slot = snapshot[key]) {
                is CacheSlot.Ready -> return slot.deferred
                is CacheSlot.Pending -> return slot.await()
                null -> {
                    val pending = CacheSlot.Pending<Value>()
                    if (!cache.compareAndSet(snapshot, snapshot + (key to pending))) continue

                    val result = runCatching(create)
                    pending.complete(result)
                    if (result.isSuccess) {
                        replacePending(key, pending, CacheSlot.Ready(result.getOrThrow()))
                    } else {
                        removePending(key, pending)
                    }
                    return result.getOrThrow()
                }
            }
        }
    }

    fun clearChunks() {
        queue.store(emptyList())
        allChunks.store(emptyList())
    }

    fun cancelAndClearCache(message: String) {
        val snapshot = takeCacheSnapshot()
        snapshot.values.forEach { it.cancel(message) }
    }

    private fun replacePending(key: Key, pending: CacheSlot.Pending<Value>, ready: CacheSlot.Ready<Value>) {
        while (true) {
            val snapshot = cache.load()
            if (snapshot[key] !== pending) return
            if (cache.compareAndSet(snapshot, snapshot + (key to ready))) return
        }
    }

    private fun removePending(key: Key, pending: CacheSlot.Pending<Value>) {
        while (true) {
            val snapshot = cache.load()
            if (snapshot[key] !== pending) return
            if (cache.compareAndSet(snapshot, snapshot - key)) return
        }
    }

    private fun takeCacheSnapshot(): Map<Key, CacheSlot<Value>> {
        while (true) {
            val snapshot = cache.load()
            if (cache.compareAndSet(snapshot, emptyMap())) return snapshot
        }
    }

    private fun <T> update(reference: AtomicReference<List<T>>, transform: (List<T>) -> List<T>) {
        while (true) {
            val snapshot = reference.load()
            if (reference.compareAndSet(snapshot, transform(snapshot))) return
        }
    }
}

@OptIn(ExperimentalAtomicApi::class)
private sealed interface CacheSlot<Value> {
    fun cancel(message: String)

    class Ready<Value>(val deferred: Deferred<Value>) : CacheSlot<Value> {
        override fun cancel(message: String) {
            deferred.cancel(CancellationException(message))
        }
    }

    class Pending<Value> : CacheSlot<Value> {
        private val result = AtomicReference<Result<Deferred<Value>>?>(null)
        private val cancellationMessage = AtomicReference<String?>(null)

        fun complete(value: Result<Deferred<Value>>) {
            result.store(value)
            cancellationMessage.load()?.let { message ->
                value.getOrNull()?.cancel(CancellationException(message))
            }
        }

        fun await(): Deferred<Value> {
            while (true) {
                result.load()?.let { return it.getOrThrow() }
            }
        }

        override fun cancel(message: String) {
            cancellationMessage.store(message)
            result.load()?.getOrNull()?.cancel(CancellationException(message))
        }
    }
}
