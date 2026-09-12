package me.rerere.tts.controller

import platform.Foundation.NSRecursiveLock

internal actual class ConcurrentLinkedQueue<E : Any> actual constructor() {
    private val lock = NSRecursiveLock()
    private val queue = ArrayDeque<E>()
    actual val size: Int get() = lock.withLock { queue.size }
    actual fun addAll(elements: Collection<E>): Boolean = lock.withLock { queue.addAll(elements) }
    actual fun poll(): E? = lock.withLock { queue.removeFirstOrNull() }
    actual fun isEmpty(): Boolean = lock.withLock { queue.isEmpty() }
    actual fun isNotEmpty(): Boolean = lock.withLock { queue.isNotEmpty() }
    actual fun clear() = lock.withLock { queue.clear() }
}

internal actual class ConcurrentHashMap<K : Any, V : Any> actual constructor() {
    private val lock = NSRecursiveLock()
    private val map = mutableMapOf<K, V>()
    actual val values: Collection<V> get() = lock.withLock { map.values.toList() }
    actual fun computeIfAbsent(key: K, create: (K) -> V): V = lock.withLock { map.getOrPut(key) { create(key) } }
    actual fun clear() = lock.withLock { map.clear() }
}

private inline fun <T> NSRecursiveLock.withLock(block: () -> T): T {
    lock()
    return try { block() } finally { unlock() }
}
