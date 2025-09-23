package me.rerere.common.utils

fun <K, V> MutableMap<K, V>.putIfAbsent(key: K, value: V) {
    if (!this.containsKey(key)) {
        this[key] = value
    }
}
