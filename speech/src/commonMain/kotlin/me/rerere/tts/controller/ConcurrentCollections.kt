package me.rerere.tts.controller

/** The JDK collection operations used by the original controller. */
internal expect class ConcurrentLinkedQueue<E : Any>() {
    val size: Int
    fun addAll(elements: Collection<E>): Boolean
    fun poll(): E?
    fun isEmpty(): Boolean
    fun isNotEmpty(): Boolean
    fun clear()
}

internal expect class ConcurrentHashMap<K : Any, V : Any>() {
    val values: Collection<V>
    fun computeIfAbsent(key: K, create: (K) -> V): V
    fun clear()
}
