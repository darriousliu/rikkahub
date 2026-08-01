package me.rerere.rikkahub.utils

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * A simple thread-safe cache implementation with expiration support.
 * This is a lightweight alternative to Guava Cache to avoid concurrency issues.
 */
@OptIn(ExperimentalAtomicApi::class)
class SimpleCache<K, V> internal constructor(
    private val expireAfterWrite: Duration,
    private val clock: Clock = Clock.System
) {
    private data class CacheEntry<V>(
        val value: V,
        val writtenAt: Instant
    ) {
        fun isExpired(expireAfterWrite: Duration, now: Instant): Boolean {
            return now - writtenAt > expireAfterWrite
        }
    }

    private val cache = AtomicReference<Map<K, CacheEntry<V>>>(emptyMap())

    fun getIfPresent(key: K): V? {
        while (true) {
            val snapshot = cache.load()
            val entry = snapshot[key] ?: return null
            if (!entry.isExpired(expireAfterWrite, currentInstant())) {
                return entry.value
            }
            if (cache.compareAndSet(snapshot, snapshot - key)) {
                return null
            }
        }
    }

    fun put(key: K, value: V) {
        updateCache { snapshot ->
            snapshot + (key to CacheEntry(value, currentInstant()))
        }
    }

    fun invalidate(key: K) {
        updateCache { it - key }
    }

    fun invalidateAll() {
        cache.store(emptyMap())
    }

    fun cleanUp() {
        updateCache { snapshot ->
            val now = currentInstant()
            snapshot.filterValues { entry ->
                !entry.isExpired(expireAfterWrite, now)
            }
        }
    }

    fun size(): Int = cache.load().size

    private fun updateCache(transform: (Map<K, CacheEntry<V>>) -> Map<K, CacheEntry<V>>) {
        while (true) {
            val snapshot = cache.load()
            val updated = transform(snapshot)
            if (cache.compareAndSet(snapshot, updated)) return
        }
    }

    private fun currentInstant(): Instant =
        Instant.fromEpochMilliseconds(clock.now().toEpochMilliseconds())

    companion object {
        fun <K, V> builder() = Builder<K, V>()
    }

    class Builder<K, V> {
        private var expireAfterWrite: Duration = Duration.INFINITE

        fun expireAfterWrite(duration: Duration): Builder<K, V> {
            expireAfterWrite = duration
            return this
        }

        fun build(): SimpleCache<K, V> {
            return SimpleCache(expireAfterWrite)
        }
    }
}
