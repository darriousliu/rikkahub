package me.rerere.rikkahub.data.ai.transformers

import kotlinx.io.files.Path
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.files.testFilesManager
import me.rerere.rikkahub.service.toLocalFilePath
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class Base64ImageStoreContractTest {
    private val root = Files.createTempDirectory("cmp-base64-image-").toFile()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val filesManager = testFilesManager(Path(root.path), scope)

    @AfterTest
    fun close() { scope.cancel(); root.deleteRecursively() }

    @Test
    fun jpegIsReencodedAsReadablePngInTheOriginalUploadDirectory() = runTest {
        val uri = assertNotNull(convert(jpeg()))
        val file = File(uri.toLocalFilePath())
        assertEquals(File(root, "upload"), file.parentFile)
        assertEquals("png", file.extension)
        Uuid.parse(file.nameWithoutExtension)
        assertContentEquals(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10), file.readBytes().take(8).toByteArray())
        val decoded = assertNotNull(ImageIO.read(file))
        assertEquals(3, decoded.width)
        assertEquals(2, decoded.height)
    }

    @Test
    fun undecodableImageFailsWithoutCreatingAnAttachment() = runTest {
        assertFailsWith<NullPointerException> { convert(byteArrayOf(1, 2, 3)) }
        assertTrue(root.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun fileWriteFailurePropagatesWithoutReplacingTheExistingFile() = runTest {
        val blocker = File(root, "upload").apply { writeText("original") }
        assertFailsWith<Exception> { convert(jpeg()) }
        assertEquals("original", blocker.readText())
        assertEquals(listOf(blocker), root.listFiles().orEmpty().toList())
    }

    private fun jpeg(): ByteArray = ByteArrayOutputStream().use { output ->
        ImageIO.write(BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB), "jpeg", output)
        output.toByteArray()
    }
    private suspend fun convert(bytes: ByteArray): String {
        val message = UIMessage.user("unused").copy(parts = listOf(
            UIMessagePart.Image("data:image/png;base64," + kotlin.io.encoding.Base64.encode(bytes)),
        ))
        return (filesManager.convertBase64ImagePartToLocalFile(message).parts.single() as UIMessagePart.Image).url
    }

}
