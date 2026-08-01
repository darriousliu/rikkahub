package me.rerere.ai.util

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

interface KeyRoulette {
    fun next(keys: String, providerId: String = ""): String

    companion object {
        fun default(): KeyRoulette = DefaultKeyRoulette()

        /**
         * LRU 轮询，持久化存储到 cacheDir/lru_key_roulette.json
         * 通过 providerId 区分同类型的多个 provider 实例，在 next() 调用时传入
         */
        fun lru(context: Context): KeyRoulette = LruKeyRoulette(
            cacheFile = File(context.cacheDir, LRU_CACHE_FILE),
            clock = Clock.System
        )

        internal fun lru(cacheFile: File, clock: Clock): KeyRoulette = LruKeyRoulette(
            cacheFile = cacheFile,
            clock = clock
        )
    }
}

private val SPLIT_KEY_REGEX = "[\\s,]+".toRegex() // 空格换行和逗号

private fun splitKey(key: String): List<String> {
    return key
        .split(SPLIT_KEY_REGEX)
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
}

private class DefaultKeyRoulette : KeyRoulette {
    override fun next(keys: String, providerId: String): String {
        val keyList = splitKey(keys)
        return if (keyList.isNotEmpty()) {
            keyList.random()
        } else {
            keys
        }
    }
}

private const val LRU_CACHE_FILE = "lru_key_roulette.json"
private val EXPIRE_DURATION = 24.hours

// 全局文件锁，防止多个 provider 实例并发读写同一文件
private object LruFileLock

// 文件结构: Map<providerId, Map<apiKey, lastUsedTimestamp>>
private typealias LruCache = Map<String, Map<String, Long>>

private class LruKeyRoulette(
    private val cacheFile: File,
    private val clock: Clock,
) : KeyRoulette {

    override fun next(keys: String, providerId: String): String {
        val keyList = splitKey(keys)
        if (keyList.isEmpty()) return keys

        synchronized(LruFileLock) {
            val now = currentInstant()
            val allCache = loadCache().toMutableMap()

            // 取本 provider 的记录，过滤掉已过期条目和不在当前 key 列表中的条目
            val providerCache = (allCache[providerId] ?: emptyMap())
                .filter { (k, lastUsed) ->
                    k in keyList && now - Instant.fromEpochMilliseconds(lastUsed) < EXPIRE_DURATION
                }
                .toMutableMap()

            // 优先选从未使用的 key，否则选最久未使用的
            val selected = keyList.firstOrNull { it !in providerCache }
                ?: providerCache.minByOrNull { it.value }!!.key

            providerCache[selected] = now.toEpochMilliseconds()
            allCache[providerId] = providerCache

            // 清理整个 provider 条目均已过期的记录
            allCache.entries.removeIf { (id, cache) ->
                id != providerId && cache.values.all {
                    now - Instant.fromEpochMilliseconds(it) >= EXPIRE_DURATION
                }
            }

            saveCache(allCache)
            return selected
        }
    }

    private fun loadCache(): LruCache {
        return try {
            if (!cacheFile.exists()) return emptyMap()
            Json.decodeFromString(cacheFile.readText())
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun saveCache(cache: LruCache) {
        try {
            cacheFile.writeText(Json.encodeToString(cache))
        } catch (_: Exception) {
        }
    }

    private fun currentInstant(): Instant =
        Instant.fromEpochMilliseconds(clock.now().toEpochMilliseconds())
}
