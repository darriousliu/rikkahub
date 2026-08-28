package me.rerere.rikkahub.data.ai.transformers

import io.github.reactivecircus.cache4k.Cache
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.cacheDir
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.div
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.parent
import io.github.vinceglb.filekit.readString
import io.github.vinceglb.filekit.writeString
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

private const val OCR_CACHE_FILE = "ocr_cache.json"

internal const val OCR_CACHE_MAX_ENTRIES = 64
internal val OCR_CACHE_TTL: Duration = 3.days

@Serializable
internal data class OcrCacheEntry(val value: String, val expiresAt: Long)

@Serializable
internal data class OcrCacheFile(val entries: Map<String, OcrCacheEntry> = emptyMap())

/**
 * Drops expired entries and keeps only the [maxEntries] that live longest, which is also the
 * newest ones because every entry gets the same TTL.
 */
internal fun pruneOcrEntries(
    entries: Map<String, OcrCacheEntry>,
    nowMillis: Long,
    maxEntries: Int = OCR_CACHE_MAX_ENTRIES,
): Map<String, OcrCacheEntry> = entries.entries
    .filter { (_, entry) -> entry.expiresAt > nowMillis }
    .sortedByDescending { (_, entry) -> entry.expiresAt }
    .take(maxEntries)
    .associate { (key, entry) -> key to entry }

/**
 * OCR results are expensive to produce, so they survive restarts in a small JSON file.
 *
 * cache4k owns eviction and expiry in memory; the file is a best-effort mirror that is
 * reloaded once per process and rewritten on every put.
 */
internal class OcrResultCache(
    private val file: PlatformFile = FileKit.cacheDir / OCR_CACHE_FILE,
    private val ttl: Duration = OCR_CACHE_TTL,
    private val clock: Clock = Clock.System,
) {
    private val mutex = Mutex()
    private val memory = Cache.Builder<String, String>()
        .maximumCacheSize(OCR_CACHE_MAX_ENTRIES.toLong())
        .expireAfterWrite(ttl)
        .build()
    private var restored = false

    suspend fun get(key: String): String? {
        restoreOnce()
        return memory.get(key)
    }

    suspend fun put(key: String, value: String) {
        restoreOnce()
        memory.put(key, value)
        mutex.withLock { persist(key, value) }
    }

    private suspend fun restoreOnce() {
        if (restored) return
        mutex.withLock {
            if (restored) return@withLock
            restored = true
            pruneOcrEntries(readEntries(), clock.now().toEpochMilliseconds())
                .forEach { (key, entry) -> memory.put(key, entry.value) }
        }
    }

    private suspend fun persist(key: String, value: String) {
        val now = clock.now().toEpochMilliseconds()
        val merged = pruneOcrEntries(
            entries = readEntries() + (key to OcrCacheEntry(value, now + ttl.inWholeMilliseconds)),
            nowMillis = now,
        )
        runCatching {
            file.parent()?.createDirectories()
            file.writeString(json.encodeToString(OcrCacheFile(merged)))
        }
    }

    private suspend fun readEntries(): Map<String, OcrCacheEntry> = runCatching {
        if (!file.exists()) return emptyMap()
        val text = file.readString()
        if (text.isBlank()) return emptyMap()
        json.decodeFromString<OcrCacheFile>(text).entries
    }.getOrElse { emptyMap() }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
