package me.rerere.rikkahub.platform

import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class JvmQrImageDecoderTest {
    @Test
    fun decodesQrImagesWithoutCameraOrExtendedIconDependencies() = runTest {
        val content = "https://rikkahub.com/desktop-test"
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 256, 256)
        val image = BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                image.setRGB(x, y, if (matrix[x, y]) 0xff000000.toInt() else 0xffffffff.toInt())
            }
        }
        val png = ByteArrayOutputStream().apply { ImageIO.write(image, "png", this) }.toByteArray()

        val loader = javaClass.classLoader
        assertNull(loader.getResource("org/bytedeco/opencv/global/opencv_core.class"))
        assertNull(loader.getResource("androidx/compose/material/icons/filled/CameraAltKt.class"))
        assertEquals(QrScanResult.Success(content), platformDecodeQrImage(png))
    }

    @Test
    fun invalidImageStillReturnsFailure() = runTest {
        assertIs<QrScanResult.Failure>(platformDecodeQrImage(byteArrayOf(1, 2, 3)))
    }
}
