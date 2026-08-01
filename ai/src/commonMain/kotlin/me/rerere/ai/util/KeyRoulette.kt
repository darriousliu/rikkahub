package me.rerere.ai.util

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

interface KeyRoulette {
    fun next(keys: String, providerId: String = ""): String

    companion object {
        fun default(): KeyRoulette = DefaultKeyRoulette()

        internal fun lru(
            storage: KeyRouletteStorage,
            clock: Clock = Clock.System,
        ): KeyRoulette = LruKeyRoulette(storage, clock)
    }
}

/** Atomically reads and replaces the serialized LRU state. */
internal fun interface KeyRouletteStorage {
    fun update(transform: (String?) -> String)
}

private val SPLIT_KEY_REGEX = "[\\s,]+".toRegex()

private fun splitKey(key: String): List<String> = key
    .split(SPLIT_KEY_REGEX)
    .map { it.trim() }
    .filter { it.isNotBlank() }
    .distinct()

private class DefaultKeyRoulette : KeyRoulette {
    override fun next(keys: String, providerId: String): String {
        val keyList = splitKey(keys)
        return if (keyList.isNotEmpty()) keyList.random() else keys
    }
}

private val EXPIRE_DURATION = 24.hours

// Serialized structure: Map<providerId, Map<apiKey, lastUsedTimestamp>>.
private typealias LruCache = Map<String, Map<String, Long>>

private class LruKeyRoulette(
    private val storage: KeyRouletteStorage,
    private val clock: Clock,
) : KeyRoulette {
    override fun next(keys: String, providerId: String): String {
        val keyList = splitKey(keys)
        if (keyList.isEmpty()) return keys

        var selected = keys
        storage.update { serialized ->
            val now = currentInstant()
            val allCache = loadCache(serialized).toMutableMap()
            val providerCache = (allCache[providerId] ?: emptyMap())
                .filter { (key, lastUsed) ->
                    key in keyList && now - Instant.fromEpochMilliseconds(lastUsed) < EXPIRE_DURATION
                }
                .toMutableMap()

            selected = keyList.firstOrNull { it !in providerCache }
                ?: providerCache.minByOrNull { it.value }!!.key

            providerCache[selected] = now.toEpochMilliseconds()
            allCache[providerId] = providerCache
            allCache.entries.removeAll { (id, cache) ->
                id != providerId && cache.values.all {
                    now - Instant.fromEpochMilliseconds(it) >= EXPIRE_DURATION
                }
            }
            Json.encodeToString(allCache)
        }
        return selected
    }

    private fun loadCache(serialized: String?): LruCache = runCatching {
        if (serialized == null) emptyMap<String, Map<String, Long>>()
        else Json.decodeFromString<LruCache>(serialized)
    }.getOrDefault(emptyMap<String, Map<String, Long>>())

    private fun currentInstant(): Instant =
        Instant.fromEpochMilliseconds(clock.now().toEpochMilliseconds())
}
