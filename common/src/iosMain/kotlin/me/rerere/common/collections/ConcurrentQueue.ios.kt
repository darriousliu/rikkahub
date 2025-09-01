package me.rerere.common.collections

import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock

actual class ConcurrentQueue<E> {
    private val delegate: ArrayDeque<E>
    private val lock = reentrantLock()

    actual constructor() {
        delegate = ArrayDeque()
    }

    actual constructor(elements: Collection<E>) {
        delegate = ArrayDeque(elements)
    }

    actual val size: Int
        get() = lock.withLock {
            delegate.size
        }

    actual fun addAll(elements: Collection<E>): Boolean = lock.withLock {
        delegate.addAll(elements)
    }

    actual fun poll(): E? = lock.withLock {
        delegate.removeFirstOrNull()
    }

    actual fun peek(): E? = lock.withLock {
        delegate.firstOrNull()
    }

    actual fun offer(element: E): Boolean = lock.withLock {
        delegate.add(element)
    }

    actual fun clear() = lock.withLock {
        delegate.clear()
    }

    actual fun isEmpty(): Boolean = lock.withLock {
        delegate.isEmpty()
    }

    actual fun isNotEmpty(): Boolean = lock.withLock {
        delegate.isNotEmpty()
    }
}
