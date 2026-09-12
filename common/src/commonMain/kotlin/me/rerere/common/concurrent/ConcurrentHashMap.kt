package me.rerere.common.concurrent

/**
 * The JDK concurrent map operations used by the shared registries.
 * Keys and values can be traversed during updates; iOS returns copies instead of live views.
 * Factories must be short and must not modify this map, as required by the original JDK API.
 */
expect class ConcurrentHashMap<K : Any, V : Any>() {
    val size: Int
    val keys: Set<K>
    val values: Collection<V>
    operator fun get(key: K): V?
    fun containsKey(key: K): Boolean
    fun put(key: K, value: V): V?
    fun putIfAbsent(key: K, value: V): V?
    fun computeIfAbsent(key: K, create: (K) -> V): V
    fun remove(key: K): V?
    fun remove(key: K, value: V): Boolean
    fun clear()
}
