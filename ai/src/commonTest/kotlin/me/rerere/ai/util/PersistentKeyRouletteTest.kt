package me.rerere.ai.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

class PersistentKeyRouletteTest {
    @Test
    fun restoresUsageAcrossInstances() = withTemporaryFile { cacheFile ->
        val clock = MutableClock(TEST_NOW)

        assertEquals("first", KeyRoulette.persistentLru(cacheFile, clock).next("first second", PROVIDER))
        assertEquals("second", KeyRoulette.persistentLru(cacheFile, clock).next("first second", PROVIDER))
    }

    @Test
    fun readsExistingAndroidJsonFormat() = withTemporaryFile { cacheFile ->
        cacheFile.writeText("""{"$PROVIDER":{"used":${TEST_NOW.toEpochMilliseconds()}}}""")

        val selected = KeyRoulette.persistentLru(cacheFile, MutableClock(TEST_NOW))
            .next("used unused", PROVIDER)

        assertEquals("unused", selected)
    }

    @Test
    fun corruptJsonStartsWithEmptyState() = withTemporaryFile { cacheFile ->
        cacheFile.writeText("not-json")

        assertEquals(
            "first",
            KeyRoulette.persistentLru(cacheFile, MutableClock(TEST_NOW)).next("first second", PROVIDER),
        )
        assertTrue(cacheFile.readText().startsWith("{"))
    }

    @Test
    fun keepsProvidersIndependent() = withTemporaryFile { cacheFile ->
        val roulette = KeyRoulette.persistentLru(cacheFile, MutableClock(TEST_NOW))

        assertEquals("first", roulette.next("first second", "provider-a"))
        assertEquals("first", roulette.next("first second", "provider-b"))
        assertEquals("second", roulette.next("first second", "provider-a"))
    }

    @Test
    fun expiresUsageAtTwentyFourHours() = withTemporaryFile { cacheFile ->
        val expiredAt = TEST_NOW.minus(24.hours).toEpochMilliseconds()
        cacheFile.writeText("""{"$PROVIDER":{"first":$expiredAt}}""")

        assertEquals(
            "first",
            KeyRoulette.persistentLru(cacheFile, MutableClock(TEST_NOW)).next("first second", PROVIDER),
        )
    }

    @Test
    fun removesKeysThatAreNoLongerConfigured() = withTemporaryFile { cacheFile ->
        val clock = MutableClock(TEST_NOW)
        val roulette = KeyRoulette.persistentLru(cacheFile, clock)
        roulette.next("removed retained", PROVIDER)
        clock.advance(1.milliseconds)
        roulette.next("removed retained", PROVIDER)
        clock.advance(1.milliseconds)

        roulette.next("retained added", PROVIDER)

        val providerState = Json.parseToJsonElement(cacheFile.readText())
            .jsonObject.getValue(PROVIDER).jsonObject
        assertFalse("removed" in providerState)
        assertTrue("retained" in providerState)
        assertTrue("added" in providerState)
    }

    @Test
    fun selectsLeastRecentlyUsedKeyAfterEveryKeyWasUsed() = withTemporaryFile { cacheFile ->
        val clock = MutableClock(TEST_NOW)
        val roulette = KeyRoulette.persistentLru(cacheFile, clock)
        assertEquals("first", roulette.next("first second", PROVIDER))
        clock.advance(1.milliseconds)
        assertEquals("second", roulette.next("first second", PROVIDER))
        clock.advance(1.milliseconds)

        assertEquals("first", roulette.next("first second", PROVIDER))
    }

    @Test
    fun writeFailureDoesNotBlockKeySelection() {
        val directory = temporaryPath("unwritable-cache")
        SystemFileSystem.createDirectories(directory)
        try {
            assertEquals(
                "first",
                KeyRoulette.persistentLru(directory, MutableClock(TEST_NOW)).next("first second", PROVIDER),
            )
        } finally {
            SystemFileSystem.delete(directory, mustExist = false)
        }
    }

    @Test
    fun concurrentInstancesClaimUnusedKeysWithoutDuplicates() = runTest {
        withTemporaryFile { cacheFile ->
            val keys = List(32) { "key-$it" }
            val configuredKeys = keys.joinToString(" ")

            val selected = keys.map {
                async(Dispatchers.Default) {
                    KeyRoulette.persistentLru(cacheFile, MutableClock(TEST_NOW)).next(configuredKeys, PROVIDER)
                }
            }.awaitAll()

            assertEquals(keys.size, selected.toSet().size)
            assertEquals(keys.toSet(), selected.toSet())
        }
    }

    private class MutableClock(
        private var instant: Instant,
    ) : Clock {
        override fun now(): Instant = instant

        fun advance(duration: Duration) {
            instant += duration
        }
    }

    private companion object {
        const val PROVIDER = "provider-id"
        val TEST_NOW = Instant.parse("2026-09-08T00:00:00Z")
    }
}

private inline fun <T> withTemporaryFile(block: (Path) -> T): T {
    val path = temporaryPath("lru-key-roulette")
    return try {
        block(path)
    } finally {
        SystemFileSystem.delete(path, mustExist = false)
    }
}

private fun temporaryPath(prefix: String): Path =
    Path(SystemTemporaryDirectory, "$prefix-${Uuid.random()}.json")

private fun Path.writeText(value: String) {
    parent?.let { SystemFileSystem.createDirectories(it) }
    SystemFileSystem.sink(this).buffered().use { it.writeString(value) }
}

private fun Path.readText(): String =
    SystemFileSystem.source(this).buffered().use { it.readString() }
