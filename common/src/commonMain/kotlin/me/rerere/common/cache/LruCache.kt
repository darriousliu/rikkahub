package me.rerere.common.cache

import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlin.time.Clock
import androidx.collection.LruCache as AndroidXLruCache

class LruCache<K, V>(
    private val capacity: Int,
    private val store: CacheStore<K, V>,
    private val deleteOnEvict: Boolean = false,
    preloadFromStore: Boolean = false,
    private val expireAfterWriteMillis: Long? = null
) where K : Any {
    private val lock = reentrantLock()

    private val map = object : AndroidXLruCache<K, CacheEntry<V>>(capacity) {
        override fun entryRemoved(
            evicted: Boolean,
            key: K,
            oldValue: CacheEntry<V>,
            newValue: CacheEntry<V>?
        ) {
            // 只有在真正被淘汰时才处理（不是替换）
            if (evicted && deleteOnEvict) {
                try {
                    store.remove(key)
                } catch (_: Exception) {
                }
            }
        }
    }

    private operator fun AndroidXLruCache<K, CacheEntry<V>>.set(key: K, value: CacheEntry<V>) {
        put(key, value)
    }


    init {
        if (preloadFromStore) {
            try {
                val all = store.loadAllEntries()
                lock.withLock {
                    val now = now()
                    for ((k, entry) in all) {
                        if (!entry.isExpired(now)) {
                            map[k] = entry
                        } else {
                            runCatching { store.remove(k) }
                        }
                        if (map.size() >= capacity) break
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    fun get(key: K): V? {
        lock.withLock {
            map[key]?.let { entry ->
                if (!entry.isExpired(now())) return entry.value
                map.remove(key)
            }
        }
        val entry = store.loadEntry(key)
        if (entry != null) {
            return if (!entry.isExpired(now())) {
                lock.withLock { map[key] = entry }
                entry.value
            } else {
                runCatching { store.remove(key) }
                null
            }
        }
        return null
    }

    fun put(key: K, value: V) = put(key, value, expireAfterWriteMillis)

    fun put(key: K, value: V, ttlMillis: Long?) {
        val entry = CacheEntry(value = value, expiresAt = ttlMillis?.let { now() + it })
        lock.withLock { map[key] = entry }
        try {
            store.saveEntry(key, entry)
        } catch (_: Exception) {
        }
    }

    fun remove(key: K) {
        lock.withLock { map.remove(key) }
        try {
            store.remove(key)
        } catch (_: Exception) {
        }
    }

    fun clear() {
        lock.withLock { map.evictAll() }
        try {
            store.clear()
        } catch (_: Exception) {
        }
    }

    fun containsKey(key: K): Boolean {
        val inMem = lock.withLock { map[key]?.let { !it.isExpired(now()) } ?: false }
        if (inMem) return true
        val entry = store.loadEntry(key)
        if (entry != null && !entry.isExpired(now())) return true
        if (entry != null) runCatching { store.remove(key) }
        return false
    }

    fun size(): Int = lock.withLock { map.size() }

    fun keysInMemory(): Set<K> = lock.withLock { map.snapshot().filterValues { !it.isExpired(now()) }.keys.toSet() }
}

private fun now(): Long = Clock.System.now().toEpochMilliseconds()

