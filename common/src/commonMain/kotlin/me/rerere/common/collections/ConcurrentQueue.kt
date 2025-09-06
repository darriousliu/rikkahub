package me.rerere.common.collections

expect class ConcurrentQueue<E>() {
    constructor(elements: Collection<E>)

    val size: Int

    fun addAll(elements: Collection<E>): Boolean
    fun poll(): E?
    fun peek(): E?
    fun offer(element: E): Boolean
    fun clear()
    fun isEmpty(): Boolean
    fun isNotEmpty(): Boolean
}
