package me.rerere.common.concurrent

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * A small synchronous map backed by immutable atomic snapshots.
 *
 * Factories passed to [getOrPut] run at most once per key, matching the behavior relied on by
 * the previous ConcurrentHashMap call sites.
 */
@OptIn(ExperimentalAtomicApi::class)
class AtomicSnapshotMap<Key, Value> {
    private val entries = AtomicReference<Map<Key, Entry<Value>>>(emptyMap())

    val size: Int
        get() = entries.load().size

    operator fun get(key: Key): Value? = when (val entry = entries.load()[key]) {
        is Entry.Ready -> entry.value
        is Entry.Pending -> entry.await()
        null -> null
    }

    fun containsKey(key: Key): Boolean = entries.load().containsKey(key)

    fun keysSnapshot(): Set<Key> = entries.load().keys

    fun valuesSnapshot(): List<Value> = entries.load().values.map { it.value }

    fun put(key: Key, value: Value): Value? {
        while (true) {
            val snapshot = entries.load()
            when (val previous = snapshot[key]) {
                is Entry.Pending -> previous.await()
                else -> {
                    val updated = snapshot + (key to Entry.Ready(value))
                    if (entries.compareAndSet(snapshot, updated)) {
                        return (previous as? Entry.Ready)?.value
                    }
                }
            }
        }
    }

    fun putIfAbsent(key: Key, value: Value): Value? {
        while (true) {
            val snapshot = entries.load()
            when (val existing = snapshot[key]) {
                is Entry.Ready -> return existing.value
                is Entry.Pending -> return existing.await()
                null -> if (entries.compareAndSet(snapshot, snapshot + (key to Entry.Ready(value)))) {
                    return null
                }
            }
        }
    }

    fun getOrPut(key: Key, create: () -> Value): Value {
        while (true) {
            val snapshot = entries.load()
            when (val existing = snapshot[key]) {
                is Entry.Ready -> return existing.value
                is Entry.Pending -> return existing.await()
                null -> {
                    val pending = Entry.Pending<Value>()
                    if (!entries.compareAndSet(snapshot, snapshot + (key to pending))) continue

                    val result = runCatching(create)
                    pending.complete(result)
                    if (result.isSuccess) {
                        replacePending(key, pending, Entry.Ready(result.getOrThrow()))
                    } else {
                        removePending(key, pending)
                    }
                    return result.getOrThrow()
                }
            }
        }
    }

    fun remove(key: Key): Value? {
        while (true) {
            val snapshot = entries.load()
            when (val existing = snapshot[key]) {
                is Entry.Pending -> existing.await()
                is Entry.Ready -> if (entries.compareAndSet(snapshot, snapshot - key)) return existing.value
                null -> return null
            }
        }
    }

    fun remove(key: Key, value: Value): Boolean {
        while (true) {
            val snapshot = entries.load()
            when (val existing = snapshot[key]) {
                is Entry.Pending -> existing.await()
                is Entry.Ready -> {
                    if (existing.value != value) return false
                    if (entries.compareAndSet(snapshot, snapshot - key)) return true
                }
                null -> return false
            }
        }
    }

    fun clear(): List<Value> {
        while (true) {
            val snapshot = entries.load()
            if (entries.compareAndSet(snapshot, emptyMap())) {
                return snapshot.values.map { it.value }
            }
        }
    }

    private fun replacePending(key: Key, pending: Entry.Pending<Value>, ready: Entry.Ready<Value>) {
        while (true) {
            val snapshot = entries.load()
            if (snapshot[key] !== pending) return
            if (entries.compareAndSet(snapshot, snapshot + (key to ready))) return
        }
    }

    private fun removePending(key: Key, pending: Entry.Pending<Value>) {
        while (true) {
            val snapshot = entries.load()
            if (snapshot[key] !== pending) return
            if (entries.compareAndSet(snapshot, snapshot - key)) return
        }
    }
}

@OptIn(ExperimentalAtomicApi::class)
private sealed interface Entry<out Value> {
    val value: Value

    class Ready<Value>(override val value: Value) : Entry<Value>

    class Pending<Value> : Entry<Value> {
        private val result = AtomicReference<Result<Value>?>(null)

        override val value: Value
            get() = await()

        fun complete(value: Result<Value>) {
            result.store(value)
        }

        fun await(): Value {
            while (true) {
                result.load()?.let { return it.getOrThrow() }
            }
        }
    }
}
