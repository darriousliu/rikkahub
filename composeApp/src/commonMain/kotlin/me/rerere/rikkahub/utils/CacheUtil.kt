package me.rerere.rikkahub.utils

import io.ktor.util.collections.ConcurrentMap
import kotlin.time.Clock
import kotlin.time.Duration

/**
 * A simple thread-safe cache implementation with expiration support.
 * This is a lightweight alternative to Guava Cache to avoid concurrency issues.
 */
class SimpleCache<K, V>(
    private val expireAfterWriteMillis: Long
) {
    private data class CacheEntry<V>(
        val value: V,
        val timestamp: Long = Clock.System.now().toEpochMilliseconds()
    ) {
        fun isExpired(expireAfterWriteMillis: Long): Boolean {
            return Clock.System.now().toEpochMilliseconds() - timestamp > expireAfterWriteMillis
        }
    }

    private val cache = ConcurrentMap<K, CacheEntry<V>>()

    fun getIfPresent(key: K): V? {
        val entry = cache[key] ?: return null
        return if (entry.isExpired(expireAfterWriteMillis)) {
            cache.remove(key)
            null
        } else {
            entry.value
        }
    }

    fun put(key: K, value: V) {
        cache[key] = CacheEntry(value)
    }

    fun invalidate(key: K) {
        cache.remove(key)
    }

    fun invalidateAll() {
        cache.clear()
    }

    fun cleanUp() {
        cache.entries.removeAll { it.value.isExpired(expireAfterWriteMillis) }
    }

    fun size(): Int = cache.size

    companion object {
        fun <K, V> builder() = Builder<K, V>()
    }

    class Builder<K, V> {
        private var expireAfterWriteMillis: Long = Long.MAX_VALUE

        fun expireAfterWrite(duration: Duration): Builder<K, V> {
            expireAfterWriteMillis = duration.inWholeMilliseconds
            return this
        }

        fun build(): SimpleCache<K, V> {
            return SimpleCache(expireAfterWriteMillis)
        }
    }
}
