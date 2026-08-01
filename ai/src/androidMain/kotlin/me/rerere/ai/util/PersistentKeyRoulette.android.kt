package me.rerere.ai.util

import android.content.Context
import java.io.File
import kotlin.time.Clock

private const val LRU_CACHE_FILE = "lru_key_roulette.json"

private object LruFileLock

fun KeyRoulette.Companion.lru(
    context: Context,
    clock: Clock = Clock.System,
): KeyRoulette = KeyRoulette.lru(
    storage = FileKeyRouletteStorage(File(context.cacheDir, LRU_CACHE_FILE)),
    clock = clock,
)

internal fun persistentKeyRoulette(context: Context): KeyRoulette = KeyRoulette.lru(context)

internal class FileKeyRouletteStorage(
    private val cacheFile: File,
) : KeyRouletteStorage {
    override fun update(transform: (String?) -> String) {
        synchronized(LruFileLock) {
            val current = runCatching {
                cacheFile.takeIf(File::exists)?.readText()
            }.getOrNull()
            val updated = transform(current)
            runCatching {
                cacheFile.parentFile?.mkdirs()
                cacheFile.writeText(updated)
            }
        }
    }
}
