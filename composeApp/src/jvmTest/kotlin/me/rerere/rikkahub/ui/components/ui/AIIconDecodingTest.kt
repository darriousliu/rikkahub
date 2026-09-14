package me.rerere.rikkahub.ui.components.ui

import androidx.compose.ui.graphics.Color
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import kotlinx.coroutines.test.runTest
import me.rerere.rikkahub.generated.resources.Res
import org.jetbrains.skia.Bitmap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AIIconDecodingTest {
    @Test
    fun monochromeSvgUsesTheRequestedColor() = runTest {
        val bitmap = decode("openai.svg", Color.Green)
        assertEquals(setOf(0xFF00FF00.toInt()), bitmap.opaqueColors())
    }

    @Test
    fun coloredSvgKeepsItsOwnFill() = runTest {
        val bitmap = decode("deepseek-color.svg", Color.Green)
        assertEquals(setOf(0xFF4D6BFE.toInt()), bitmap.opaqueColors())
    }

    @Test
    fun pngStillUsesTheRasterDecoder() = runTest {
        val light = decode("bing.png", Color.Black)
        val dark = decode("bing.png", Color.White)
        assertTrue(light.opaqueColors().isNotEmpty())
        assertEquals(light.opaqueColors(), dark.opaqueColors())
    }

    private suspend fun decode(path: String, color: Color): Bitmap {
        val loader = ImageLoader.Builder(PlatformContext.INSTANCE).build()
        try {
            val result = loader.execute(
                ImageRequest.Builder(PlatformContext.INSTANCE)
                    .data(Res.getUri("files/icons/$path"))
                    .decoderFactory(aiIconSvgDecoder(color))
                    .size(128, 128)
                    .build()
            )
            return assertIs<SuccessResult>(result, "$path: $result").image.toBitmap()
        } finally {
            loader.shutdown()
        }
    }

    private fun Bitmap.opaqueColors(): Set<Int> = buildSet {
        for (y in 0 until height) {
            for (x in 0 until width) {
                val color = getColor(x, y)
                if (color ushr 24 == 255) add(color)
            }
        }
    }
}
