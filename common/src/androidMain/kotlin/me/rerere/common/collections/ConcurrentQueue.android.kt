package me.rerere.common.collections

import java.util.concurrent.ConcurrentLinkedQueue

actual class ConcurrentQueue<E> {
    private val delegate: ConcurrentLinkedQueue<E>

    actual constructor() {
        delegate = ConcurrentLinkedQueue()
    }

    actual constructor(elements: Collection<E>) {
        delegate = ConcurrentLinkedQueue(elements)
    }

    actual val size: Int
        get() = delegate.size

    actual fun addAll(elements: Collection<E>): Boolean {
        return delegate.addAll(elements)
    }

    actual fun poll(): E? {
        return delegate.poll()
    }

    actual fun peek(): E? {
        return delegate.peek()
    }

    actual fun offer(element: E): Boolean {
        return delegate.offer(element)
    }

    actual fun clear() {
        delegate.clear()
    }

    actual fun isEmpty(): Boolean {
        return delegate.isEmpty()
    }

    actual fun isNotEmpty(): Boolean {
        return delegate.isNotEmpty()
    }
}
