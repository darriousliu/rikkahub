package me.rerere.tts.controller

internal actual class ConcurrentLinkedQueue<E : Any> actual constructor() {
    private val queue = java.util.concurrent.ConcurrentLinkedQueue<E>()
    actual val size: Int get() = queue.size
    actual fun addAll(elements: Collection<E>): Boolean = queue.addAll(elements)
    actual fun poll(): E? = queue.poll()
    actual fun isEmpty(): Boolean = queue.isEmpty()
    actual fun isNotEmpty(): Boolean = queue.isNotEmpty()
    actual fun clear() = queue.clear()
}

internal actual class ConcurrentHashMap<K : Any, V : Any> actual constructor() {
    private val map = java.util.concurrent.ConcurrentHashMap<K, V>()
    actual val values: Collection<V> get() = map.values
    actual fun computeIfAbsent(key: K, create: (K) -> V): V = map.computeIfAbsent(key) { create(it) }
    actual fun clear() = map.clear()
}
