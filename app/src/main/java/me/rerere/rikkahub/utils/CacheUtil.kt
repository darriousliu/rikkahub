package me.rerere.rikkahub.utils

import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * A simple thread-safe cache implementation with expiration support.
 * This is a lightweight alternative to Guava Cache to avoid concurrency issues.
 */
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

    private val cache = ConcurrentHashMap<K, CacheEntry<V>>()

    fun getIfPresent(key: K): V? {
        val entry = cache[key] ?: return null
        return if (entry.isExpired(expireAfterWrite, currentInstant())) {
            cache.remove(key)
            null
        } else {
            entry.value
        }
    }

    fun put(key: K, value: V) {
        cache[key] = CacheEntry(value, currentInstant())
    }

    fun invalidate(key: K) {
        cache.remove(key)
    }

    fun invalidateAll() {
        cache.clear()
    }

    fun cleanUp() {
        cache.entries.removeIf {
            it.value.isExpired(expireAfterWrite, currentInstant())
        }
    }

    fun size(): Int = cache.size

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
