package me.rerere.rikkahub.ui.hooks

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ImageSaverTest {
    @Test
    fun savesOriginalLocalBytesAndConvertsDataAndRemoteImagesToPng() = runTest {
        val image = BufferedImage(4, 3, BufferedImage.TYPE_INT_RGB)
        image.setRGB(1, 1, 0x12ab34)
        val bytes = ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
        val file = Files.createTempFile("webview-image space-", ".png").toFile()
        val client = HttpClient(MockEngine { respond(bytes) })
        try {
            file.writeBytes(bytes)
            assertContentEquals(bytes, readImageForSave(file.absolutePath, client))
            assertContentEquals(bytes, readImageForSave(file.toURI().toString(), client))
            for (source in listOf("data:image/png;base64,${Base64.encode(bytes)}", "https://example.test/image")) {
                val result = assertNotNull(readImageForSave(source, client))
                val decoded = ImageIO.read(ByteArrayInputStream(result))
                assertEquals(4, decoded.width)
                assertEquals(3, decoded.height)
                assertEquals(image.getRGB(1, 1), decoded.getRGB(1, 1))
            }
        } finally {
            client.close()
            file.delete()
        }
    }

    @Test
    fun retainsOriginalNon200DownloadResult() = runTest {
        val client = HttpClient(MockEngine { respond("missing", HttpStatusCode.NotFound) })
        try {
            assertNull(readImageForSave("https://example.test/missing", client))
        } finally {
            client.close()
        }
    }
}
