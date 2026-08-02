package me.rerere.rikkahub.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.test.Test
import kotlin.test.assertEquals

class HslColorTest {
    @Test
    fun convertsPrimaryColorsToExpectedHsl() {
        assertHsl(Color.Red, hue = 0f, saturation = 1f, lightness = 0.5f)
        assertHsl(Color.Green, hue = 120f, saturation = 1f, lightness = 0.5f)
        assertHsl(Color.Blue, hue = 240f, saturation = 1f, lightness = 0.5f)
    }

    @Test
    fun roundTripsRepresentativeThemeColors() {
        listOf(
            Color(0xFF6750A4),
            Color(0xFF625B71),
            Color(0xFF7D5260),
            Color(0xFF121212),
            Color(0xFFFDF8FD),
        ).forEach { color ->
            assertEquals(color.toArgb(), colorFromHsl(color.toHsl()).toArgb())
        }
    }

    private fun assertHsl(
        color: Color,
        hue: Float,
        saturation: Float,
        lightness: Float,
    ) {
        val actual = color.toHsl()
        assertEquals(hue, actual[0], absoluteTolerance = 0.001f)
        assertEquals(saturation, actual[1], absoluteTolerance = 0.001f)
        assertEquals(lightness, actual[2], absoluteTolerance = 0.001f)
    }
}
