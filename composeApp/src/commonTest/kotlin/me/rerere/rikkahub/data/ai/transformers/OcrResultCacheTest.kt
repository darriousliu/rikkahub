package me.rerere.rikkahub.data.ai.transformers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OcrResultCacheTest {
    private fun entry(expiresAt: Long) = OcrCacheEntry(value = "ocr-$expiresAt", expiresAt = expiresAt)

    @Test
    fun `drops entries that already expired`() {
        val pruned = pruneOcrEntries(
            entries = mapOf(
                "fresh" to entry(2_000L),
                "expired" to entry(500L),
                "exactly now" to entry(1_000L),
            ),
            nowMillis = 1_000L,
        )

        assertEquals(setOf("fresh"), pruned.keys)
    }

    @Test
    fun `keeps the longest lived entries when over capacity`() {
        val entries = (1..10).associate { index -> "key$index" to entry(1_000L + index) }

        val pruned = pruneOcrEntries(entries = entries, nowMillis = 0L, maxEntries = 3)

        assertEquals(3, pruned.size)
        assertEquals(setOf("key10", "key9", "key8"), pruned.keys)
    }

    @Test
    fun `keeps every entry when under capacity`() {
        val entries = mapOf("a" to entry(5_000L), "b" to entry(6_000L))

        val pruned = pruneOcrEntries(entries = entries, nowMillis = 0L, maxEntries = 64)

        assertEquals(entries, pruned)
    }

    @Test
    fun `returns nothing when every entry expired`() {
        val pruned = pruneOcrEntries(
            entries = mapOf("a" to entry(10L), "b" to entry(20L)),
            nowMillis = 100L,
        )

        assertTrue(pruned.isEmpty())
    }

    @Test
    fun `re-adding a key replaces the older entry`() {
        val existing = mapOf("image" to entry(1_000L))

        val merged = pruneOcrEntries(
            entries = existing + ("image" to OcrCacheEntry(value = "newer", expiresAt = 9_000L)),
            nowMillis = 0L,
        )

        assertEquals(1, merged.size)
        assertEquals("newer", merged.getValue("image").value)
        assertFalse(merged.getValue("image").expiresAt == 1_000L)
    }
}
