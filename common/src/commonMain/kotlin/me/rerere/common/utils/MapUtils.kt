package me.rerere.common.utils

fun <K, V> MutableMap<K, V>.putIfAbsent(key: K, value: V) {
    if (!this.containsKey(key)) {
        this[key] = value
    }
}

expect fun <K, V> Map<K, V>.toSortedMap1(comparator: Comparator<in K>): Map<K, V>
