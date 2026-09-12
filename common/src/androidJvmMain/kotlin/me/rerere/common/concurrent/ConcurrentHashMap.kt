package me.rerere.common.concurrent

actual class ConcurrentHashMap<K : Any, V : Any> actual constructor() {
    private val map = java.util.concurrent.ConcurrentHashMap<K, V>()

    actual val size: Int get() = map.size
    actual val keys: Set<K> get() = map.keys
    actual val values: Collection<V> get() = map.values
    actual operator fun get(key: K): V? = map[key]
    actual fun containsKey(key: K): Boolean = map.containsKey(key)
    actual fun put(key: K, value: V): V? = map.put(key, value)
    actual fun putIfAbsent(key: K, value: V): V? = map.putIfAbsent(key, value)
    actual fun computeIfAbsent(key: K, create: (K) -> V): V = map.computeIfAbsent(key) { create(it) }
    actual fun remove(key: K): V? = map.remove(key)
    actual fun remove(key: K, value: V): Boolean = map.remove(key, value)
    actual fun clear() = map.clear()
}
