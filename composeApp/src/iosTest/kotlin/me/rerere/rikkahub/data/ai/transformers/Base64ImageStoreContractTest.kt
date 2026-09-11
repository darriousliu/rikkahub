package me.rerere.rikkahub.data.ai.transformers

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.readBytes
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import kotlinx.io.files.SystemTemporaryDirectory
import me.rerere.rikkahub.platform.FileKitPlatformFileStore
import me.rerere.rikkahub.service.toLocalFilePath
import me.rerere.rikkahub.utils.canonicalFile
import me.rerere.rikkahub.utils.deleteRecursively
import me.rerere.rikkahub.utils.listFiles
import me.rerere.rikkahub.utils.mkdirs
import me.rerere.rikkahub.utils.readText
import me.rerere.rikkahub.utils.resolve
import me.rerere.rikkahub.utils.writeText
import platform.UIKit.UIImage
import kotlin.io.encoding.Base64
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class Base64ImageStoreContractTest {
    private val root = Path(SystemTemporaryDirectory, "cmp-base64-image-${Uuid.random()}")
        .canonicalFile.apply { mkdirs() }
    private val store = SharedBase64ImageStore(FileKitPlatformFileStore(PlatformFile(root.toString())))
    private val png = Base64.decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+ip1sAAAAASUVORK5CYII=",
    )

    @AfterTest
    fun close() { root.deleteRecursively() }

    @OptIn(ExperimentalForeignApi::class)
    @Test
    fun writesReadablePngToTheOriginalUploadDirectory() = runTest {
        val file = Path(store.storeAsPng(png).toLocalFilePath()).canonicalFile
        assertEquals(root.resolve("upload"), file.parent)
        assertEquals("png", file.name.substringAfterLast('.'))
        Uuid.parse(file.name.substringBeforeLast('.'))
        val bytes = PlatformFile(file.toString()).readBytes()
        assertContentEquals(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10), bytes.take(8).toByteArray())
        val image = assertNotNull(UIImage.imageWithContentsOfFile(file.toString()))
        image.size.useContents {
            assertEquals(1.0, width)
            assertEquals(1.0, height)
        }
    }

    @Test
    fun undecodableImageFailsWithoutCreatingAnAttachment() = runTest {
        assertFailsWith<NullPointerException> { store.storeAsPng(byteArrayOf(1, 2, 3)) }
        assertTrue(root.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun fileWriteFailurePropagatesWithoutReplacingTheExistingFile() = runTest {
        val blocker = root.resolve("upload").apply { writeText("original") }
        assertFailsWith<Exception> { store.storeAsPng(png) }
        assertEquals("original", blocker.readText())
        assertEquals(listOf(blocker), root.listFiles().orEmpty())
    }
}
