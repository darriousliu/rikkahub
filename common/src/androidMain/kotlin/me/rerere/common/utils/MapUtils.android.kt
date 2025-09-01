package me.rerere.common.utils

actual fun <K, V> Map<K, V>.toSortedMap1(comparator: Comparator<in K>): Map<K, V> {
    return toSortedMap(comparator)
}
