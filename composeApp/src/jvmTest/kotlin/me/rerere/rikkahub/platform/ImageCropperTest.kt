package me.rerere.rikkahub.platform

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.IntSize
import io.github.vinceglb.filekit.PlatformFile
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.io.path.createTempDirectory
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

class ImageCropperTest {
    @Test
    fun avatarStaysSquareAndExportsOnlyTheVisibleCenter() {
        val source = ImageBitmap(600, 300)
        Canvas(source).apply {
            drawRect(0f, 0f, 600f, 300f, paint(Color.Green))
            drawRect(0f, 0f, 150f, 300f, paint(Color.Blue))
            drawRect(450f, 0f, 600f, 300f, paint(Color.Red))
        }
        val state = state(source, square = true)
        val region = state.region
        state.resize(0, Offset(90f, 50f), 40f)
        assertEquals(region, state.region)
        val result = renderCrop(source, state.selection(), IntSize(4096, 4096))
        val png = assertNotNull(ImageIO.read(ByteArrayInputStream(result.encodeCropPng())))
        assertEquals(300, png.width)
        assertEquals(300, png.height)
        for (x in listOf(0, 150, 299)) assertEquals(Color.Green.toArgb(), png.getRGB(x, 150))
    }

    @Test
    fun rotationAndMirrorsMatchVisibleAxesAndCanBeReset() {
        val source = quadrants()
        val state = state(source)
        state.update(state.transform.copy(rotation = 90f))
        assertCorners(source, state, listOf(Color.Blue, Color.Red, Color.Yellow, Color.Green))
        state.mirror(horizontal = true)
        assertCorners(source, state, listOf(Color.Red, Color.Blue, Color.Green, Color.Yellow))
        state.mirror(horizontal = false)
        assertCorners(source, state, listOf(Color.Green, Color.Yellow, Color.Red, Color.Blue))
        state.reset()
        assertCorners(source, state, listOf(Color.Red, Color.Green, Color.Blue, Color.Yellow))
    }

    @Test
    fun draggingZoomingAndArbitraryRotationKeepTheCropCovered() {
        val source = IntSize(600, 400)
        val crop = Rect(50f, 60f, 350f, 280f)
        for (angle in -180..180 step 7) {
            for (pan in listOf(Offset(999f, -999f), Offset(-999f, 999f), Offset.Zero)) {
                val transform = constrainCropTransform(CropTransform(pan, 0.01f, angle.toFloat()), source, crop)
                val radians = Math.toRadians(-angle.toDouble())
                for (corner in listOf(crop.topLeft, crop.topRight, crop.bottomLeft, crop.bottomRight)) {
                    val delta = corner - transform.center
                    val x = (delta.x * cos(radians) - delta.y * sin(radians)) / transform.scale
                    val y = (delta.x * sin(radians) + delta.y * cos(radians)) / transform.scale
                    assertTrue(abs(x) <= source.width / 2 + 0.001, "x=$x, angle=$angle")
                    assertTrue(abs(y) <= source.height / 2 + 0.001, "y=$y, angle=$angle")
                }
            }
        }
    }

    @Test
    fun freeCropResizesAndHigherResolutionExportKeepsTheSameRegion() {
        val preview = quadrants(80)
        val full = quadrants(800)
        val state = state(preview)
        state.resize(2, Offset(-state.region.width / 2, -state.region.height / 2), 1f)
        val result = renderCrop(full, state.selection(), IntSize(256, 200))
        assertEquals(200, result.width)
        assertEquals(200, result.height)
        assertEquals(Color.Red, result.toPixelMap()[100, 100])
        state.gesture(state.region.center, Offset.Zero, 2f, 0f)
        assertEquals(Color.Red, renderCrop(full, state.selection(), IntSize(256, 200)).toPixelMap()[100, 100])
    }

    @Test
    fun largeJpegAndPngAreSampledBeforeUseAndSourceIsUnchanged() {
        val folder = createTempDirectory("crop-sampling").toFile()
        try {
            val original = BufferedImage(6000, 4000, BufferedImage.TYPE_INT_RGB)
            original.createGraphics().apply {
                color = java.awt.Color.GREEN
                fillRect(0, 0, original.width, original.height)
                dispose()
            }
            for (format in listOf("jpg", "png")) {
                val file = File(folder, "large.$format")
                assertTrue(ImageIO.write(original, format, file))
                val before = file.readBytes()
                val sampled = readCropImage(PlatformFile(file), CROP_PREVIEW_SIZE)
                assertEquals(1500, sampled.width)
                assertEquals(1000, sampled.height)
                assertTrue(sampled.toPixelMap()[750, 500].green > 0.95f)
                assertTrue(before.contentEquals(file.readBytes()))
            }
            original.flush()
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun webpUsesSampledSkiaDecoderAndKeepsOrientation() {
        val folder = createTempDirectory("crop-webp").toFile()
        try {
            val source = quadrants(600)
            val file = File(folder, "source.webp")
            Image.makeFromBitmap(source.asSkiaBitmap()).use { image ->
                image.encodeToData(EncodedImageFormat.WEBP)?.use { file.writeBytes(it.bytes) }
            }
            val sampled = readCropImage(PlatformFile(file), 120)
            assertEquals(120, sampled.width)
            assertEquals(120, sampled.height)
            val pixels = sampled.toPixelMap()
            assertTrue(pixels[10, 10].red > 0.9f)
            assertTrue(pixels[110, 10].green > 0.9f)
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun exifCameraOrientationIsAppliedAfterSampling() {
        val source = quadrants()
        val expected = mapOf(
            2 to listOf(Color.Green, Color.Red, Color.Yellow, Color.Blue),
            3 to listOf(Color.Yellow, Color.Blue, Color.Green, Color.Red),
            4 to listOf(Color.Blue, Color.Yellow, Color.Red, Color.Green),
            5 to listOf(Color.Red, Color.Blue, Color.Green, Color.Yellow),
            6 to listOf(Color.Blue, Color.Red, Color.Yellow, Color.Green),
            7 to listOf(Color.Yellow, Color.Green, Color.Blue, Color.Red),
            8 to listOf(Color.Green, Color.Yellow, Color.Red, Color.Blue),
        )
        for ((orientation, colors) in expected) {
            val image = orientCropImage(source, orientation)
            assertEquals(colors, corners(image))
        }
    }

    private fun state(image: ImageBitmap, square: Boolean = false) = ImageCropState(
        IntSize(image.width, image.height),
        ImageCropRequest(PlatformFile(File("unused.png")), if (square) 1f to 1f else null, !square),
    ).apply { layout(Size(600f, 600f)) }

    private fun quadrants(size: Int = 80): ImageBitmap = ImageBitmap(size, size).also { image ->
        Canvas(image).apply {
            val half = size / 2f
            drawRect(0f, 0f, half, half, paint(Color.Red))
            drawRect(half, 0f, size.toFloat(), half, paint(Color.Green))
            drawRect(0f, half, half, size.toFloat(), paint(Color.Blue))
            drawRect(half, half, size.toFloat(), size.toFloat(), paint(Color.Yellow))
        }
    }

    private fun assertCorners(image: ImageBitmap, state: ImageCropState, expected: List<Color>) {
        assertEquals(expected, corners(renderCrop(image, state.selection(), IntSize(4096, 4096))))
    }

    private fun corners(image: ImageBitmap): List<Color> = image.toPixelMap().let {
        listOf(it[2, 2], it[image.width - 3, 2], it[2, image.height - 3], it[image.width - 3, image.height - 3])
    }

    private fun paint(value: Color) = Paint().apply { color = value; isAntiAlias = false }
}
