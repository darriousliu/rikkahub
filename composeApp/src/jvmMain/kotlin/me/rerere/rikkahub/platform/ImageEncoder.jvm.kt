package me.rerere.rikkahub.platform

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

internal actual suspend fun encodeImageToPng(bytes: ByteArray): ByteArray? {
    val image = runCatching { ImageIO.read(ByteArrayInputStream(bytes)) }.getOrNull() ?: return null
    return ByteArrayOutputStream().use { output ->
        if (!ImageIO.write(image, "png", output)) return null
        output.toByteArray()
    }
}
