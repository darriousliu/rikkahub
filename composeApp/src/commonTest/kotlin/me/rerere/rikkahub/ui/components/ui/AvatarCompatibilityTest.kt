package me.rerere.rikkahub.ui.components.ui

import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.test.runTest
import me.rerere.rikkahub.utils.EmojiUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AvatarCompatibilityTest {
    @Test
    fun proceduralColorsMatchOriginalSha1Goldens() {
        val cases = listOf(
            Triple("RikkaHub", 0xff42c8d7.toInt(), 0xffd742c8.toInt()),
            Triple("Alice", 0xffd76242.toInt(), 0xff42d762.toInt()),
            Triple("兔子", 0xffd74264.toInt(), 0xff64d742.toInt()),
            Triple("John Doe", 0xff9b42d7.toInt(), 0xffd79b42.toInt()),
            Triple("?", 0xffafd742.toInt(), 0xff42afd7.toInt()),
        )
        for ((name, from, to) in cases) {
            val colors = vercelAvatarColors(name)
            assertEquals(from, colors.first.toArgb(), name)
            assertEquals(to, colors.second.toArgb(), name)
        }
    }

    @Test
    fun sharedResourceKeepsOriginalCategoriesEmojiAndSkinToneOrder() = runTest {
        val data = EmojiUtils.loadEmoji()
        assertEquals("16.0.0", data.version)
        assertEquals(10, data.categories.size)
        val emojis = data.categories.flatMap { it.subCategories }.flatMap { it.emojis }
        assertEquals(3790, emojis.size)
        assertEquals("😀", emojis.first { it.name == "grinning face" }.emoji)
        val thumbs = emojis.filter { it.code.firstOrNull() == "1F44D" && it.code.size <= 2 }
        val variants = EmojiUtils.groupEmojisByVariants(thumbs).values.single()
        assertEquals(
            listOf(null, "1F3FB", "1F3FC", "1F3FD", "1F3FE", "1F3FF"),
            variants.map { it.code.getOrNull(1) },
        )
    }

    @Test
    fun unicodeConversionKeepsSupplementaryCharactersAndRangeErrors() {
        assertEquals("😀", EmojiUtils.codeToEmoji(listOf("1F600")))
        assertEquals("🇨🇳", EmojiUtils.codeToEmoji(listOf("1F1E8", "1F1F3")))
        assertEquals("👍🏽", EmojiUtils.codeToEmoji(listOf("1F44D", "1F3FD")))
        assertEquals("A", EmojiUtils.codeToEmoji(listOf("0041")))
        assertFailsWith<IllegalArgumentException> { EmojiUtils.codeToEmoji(listOf("110000")) }
    }
}
