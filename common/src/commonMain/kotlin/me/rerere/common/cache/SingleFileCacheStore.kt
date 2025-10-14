package me.rerere.common.cache

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.readString
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.json.Json
import me.rerere.common.utils.delete

class SingleFileCacheStore<K : Any, V : Any>(
    private val file: PlatformFile,
    private val keySerializer: KSerializer<K>,
    private val valueSerializer: KSerializer<V>,
    private val json: Json = Json { prettyPrint = false; ignoreUnknownKeys = true; allowStructuredMapKeys = true }
) : CacheStore<K, V> {
    private val lock = reentrantLock()
    private val entrySerializer = cacheEntrySerializer(valueSerializer)
    private val mapEntrySerializer = MapSerializer(keySerializer, entrySerializer)

    override fun loadEntry(key: K): CacheEntry<V>? = lock.withLock {
        val all = safeReadMap()
        all[key]
    }

    override fun saveEntry(key: K, entry: CacheEntry<V>) = lock.withLock {
        val all = safeReadMap().toMutableMap()
        all[key] = entry
        safeWriteMap(all)
    }

    override fun remove(key: K) = lock.withLock {
        val all = safeReadMap().toMutableMap()
        if (all.remove(key) != null) {
            safeWriteMap(all)
        }
    }

    override fun clear() = lock.withLock {
        if (file.exists()) {
            if (!file.delete()) {
                safeWriteMap(emptyMap())
            }
        }
    }

    override fun loadAllEntries(): Map<K, CacheEntry<V>> = lock.withLock { safeReadMap() }

    override fun keys(): Set<K> = lock.withLock { safeReadMap().keys }

    private fun safeReadMap(): Map<K, CacheEntry<V>> {
        try {
            if (!file.exists()) return emptyMap()
            val text = runBlocking { file.readString() }
            if (text.isBlank()) return emptyMap()
            return json.decodeFromString(mapEntrySerializer, text)
        } catch (_: Exception) {
            return try {
                val legacyMap = json.decodeFromString(
                    MapSerializer(keySerializer, valueSerializer),
                    runBlocking { file.readString() }
                )
                legacyMap.mapValues { CacheEntry(value = it.value, expiresAt = null) }
            } catch (_: Exception) {
                emptyMap()
            }
        }
    }

    private fun safeWriteMap(map: Map<K, CacheEntry<V>>) {
        ensureParentDir(file)
        val text = json.encodeToString(mapEntrySerializer, map)
        atomicWrite(file, text)
    }
}

