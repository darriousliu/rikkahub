package me.rerere.ai.util

import android.content.Context
import kotlinx.io.files.Path
import kotlin.time.Clock

private const val LRU_CACHE_FILE = "lru_key_roulette.json"

fun KeyRoulette.Companion.lru(
    context: Context,
    clock: Clock = Clock.System,
): KeyRoulette = KeyRoulette.persistentLru(
    cacheFile = Path(context.cacheDir.absolutePath, LRU_CACHE_FILE),
    clock = clock,
)

internal fun persistentKeyRoulette(context: Context): KeyRoulette = KeyRoulette.lru(context)
