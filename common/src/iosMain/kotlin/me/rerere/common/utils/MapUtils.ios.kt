package me.rerere.common.utils

actual fun <K, V> Map<K, V>.toSortedMap1(comparator: Comparator<in K>): Map<K, V> {
    return buildMap {
        this@toSortedMap1.keys.sortedWith(comparator).forEach { key ->
            this[key] = this@toSortedMap1.getValue(key)
        }
    }
}
