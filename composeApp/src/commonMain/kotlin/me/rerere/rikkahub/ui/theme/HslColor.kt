package me.rerere.rikkahub.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal fun Color.toHsl(): FloatArray {
    val maxComponent = max(red, max(green, blue))
    val minComponent = min(red, min(green, blue))
    val delta = maxComponent - minComponent
    val lightness = (maxComponent + minComponent) / 2f
    val saturation = if (delta == 0f) {
        0f
    } else {
        delta / (1f - abs(2f * lightness - 1f))
    }
    val hue = when {
        delta == 0f -> 0f
        maxComponent == red -> 60f * (((green - blue) / delta) % 6f)
        maxComponent == green -> 60f * (((blue - red) / delta) + 2f)
        else -> 60f * (((red - green) / delta) + 4f)
    }.let { if (it < 0f) it + 360f else it }
    return floatArrayOf(hue, saturation, lightness)
}

internal fun colorFromHsl(hsl: FloatArray): Color {
    require(hsl.size >= 3) { "HSL requires three components" }
    val hue = ((hsl[0] % 360f) + 360f) % 360f
    val saturation = hsl[1].coerceIn(0f, 1f)
    val lightness = hsl[2].coerceIn(0f, 1f)
    val chroma = (1f - abs(2f * lightness - 1f)) * saturation
    val intermediate = chroma * (1f - abs((hue / 60f) % 2f - 1f))
    val (red, green, blue) = when {
        hue < 60f -> Triple(chroma, intermediate, 0f)
        hue < 120f -> Triple(intermediate, chroma, 0f)
        hue < 180f -> Triple(0f, chroma, intermediate)
        hue < 240f -> Triple(0f, intermediate, chroma)
        hue < 300f -> Triple(intermediate, 0f, chroma)
        else -> Triple(chroma, 0f, intermediate)
    }
    val match = lightness - chroma / 2f
    return Color(
        red = red + match,
        green = green + match,
        blue = blue + match,
        alpha = 1f,
    )
}
