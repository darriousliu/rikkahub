package me.rerere.rikkahub.ui.pages.debug

import androidx.compose.ui.graphics.Color
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class DebugPageCompatibilityTest {
    @Test
    fun keepsAndroidArgbFormattingIncludingAlphaAndLeadingZeros() {
        val random = Random(52)
        val colors = listOf(0, 1, 0x000012AB, 0x00123456, 0x7F123456, Int.MIN_VALUE, -1) +
            List(1_024) { random.nextInt() }

        colors.forEach { argb ->
            assertEquals("#%08X".format(argb), Color(argb).toHexString(), "ARGB: $argb")
        }
    }
}
