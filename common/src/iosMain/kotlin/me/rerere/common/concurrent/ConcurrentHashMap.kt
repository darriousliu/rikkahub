package me.rerere.common.concurrent

import platform.Foundation.NSRecursiveLock

actual class ConcurrentHashMap<K : Any, V : Any> actual constructor() {
    private val lock = NSRecursiveLock()
    private val map = mutableMapOf<K, V>()

    actual val size: Int get() = lock.withLock { map.size }
    actual val keys: Set<K> get() = lock.withLock { map.keys.toSet() }
    actual val values: Collection<V> get() = lock.withLock { map.values.toList() }
    actual operator fun get(key: K): V? = lock.withLock { map[key] }
    actual fun containsKey(key: K): Boolean = lock.withLock { map.containsKey(key) }
    actual fun put(key: K, value: V): V? = lock.withLock { map.put(key, value) }
    actual fun putIfAbsent(key: K, value: V): V? = lock.withLock {
        map[key]?.let { return@withLock it }
        map.put(key, value)
    }
    actual fun computeIfAbsent(key: K, create: (K) -> V): V = lock.withLock {
        map.getOrPut(key) { create(key) }
    }
    actual fun remove(key: K): V? = lock.withLock { map.remove(key) }
    actual fun remove(key: K, value: V): Boolean = lock.withLock {
        if (map[key] != value) return@withLock false
        map.remove(key)
        true
    }
    actual fun clear() = lock.withLock { map.clear() }
}

private inline fun <T> NSRecursiveLock.withLock(block: () -> T): T {
    lock()
    return try { block() } finally { unlock() }
}
