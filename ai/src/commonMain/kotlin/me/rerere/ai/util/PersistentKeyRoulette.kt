package me.rerere.ai.util

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlin.time.Clock

/** Persists key usage in [cacheFile], retaining the Android LRU cache format and expiry policy. */
fun KeyRoulette.Companion.persistentLru(
    cacheFile: Path,
    clock: Clock = Clock.System,
): KeyRoulette = KeyRoulette.lru(
    storage = FileKeyRouletteStorage(cacheFile),
    clock = clock,
)

internal class FileKeyRouletteStorage(
    private val cacheFile: Path,
) : KeyRouletteStorage {
    override fun update(transform: (String?) -> String) {
        withKeyRouletteFileLock {
            val current = runCatching {
                SystemFileSystem.source(cacheFile).buffered().use { it.readString() }
            }.getOrNull()
            val updated = transform(current)
            // Cache failures must not prevent the provider request, matching the Android implementation.
            runCatching {
                cacheFile.parent?.let { SystemFileSystem.createDirectories(it) }
                SystemFileSystem.sink(cacheFile).buffered().use { it.writeString(updated) }
            }
        }
    }
}

/** Serializes read/modify/write across roulette instances in this process. */
internal expect fun <T> withKeyRouletteFileLock(block: () -> T): T
