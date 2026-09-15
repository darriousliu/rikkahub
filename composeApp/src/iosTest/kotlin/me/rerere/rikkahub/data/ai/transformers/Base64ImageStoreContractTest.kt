package me.rerere.rikkahub.data.ai.transformers

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.readBytes
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import me.rerere.rikkahub.data.db.createIosAppDatabase
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.files.saveUploadFromBytes
import me.rerere.rikkahub.data.repository.FilesRepository
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
    private val workRoot = Path(SystemTemporaryDirectory, "cmp-base64-image-${Uuid.random()}")
        .canonicalFile.apply { mkdirs() }
    private val root = workRoot.resolve("files").apply { mkdirs() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val database = createIosAppDatabase(PlatformFile(workRoot.resolve("database/rikka_hub").toString()))
    private val filesManager = FilesManager(root, FilesRepository(database.managedFileDao()), scope, asyncFileIo = true)
    private val png = Base64.decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+ip1sAAAAASUVORK5CYII=",
    )

    @AfterTest
    fun close() { scope.cancel(); database.close(); workRoot.deleteRecursively() }

    @OptIn(ExperimentalForeignApi::class)
    @Test
    fun writesReadablePngToTheOriginalUploadDirectory() = runTest {
        val file = Path(convert(png).toLocalFilePath()).canonicalFile
        assertEquals(root.resolve("upload"), file.parent)
        assertEquals("png", file.name.substringAfterLast('.'))
        Uuid.parse(file.name.substringBeforeLast('.'))
        val bytes = PlatformFile(file.toString()).readBytes()
        assertContentEquals(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10), bytes.take(8).toByteArray())
        val image = assertNotNull(UIImage.imageWithContentsOfFile(file.toString()))
        val record = withContext(Dispatchers.Default) {
            withTimeout(5_000) { filesManager.observe().first { it.size == 1 }.single() }
        }
        assertEquals("image.png", record.displayName)
        assertEquals("image/png", record.mimeType)
        assertEquals(bytes.size.toLong(), record.sizeBytes)
        assertEquals(record, filesManager.get(record.id))
        image.size.useContents {
            assertEquals(1.0, width)
            assertEquals(1.0, height)
        }
    }

    @Test
    fun undecodableImageFailsWithoutCreatingAnAttachment() = runTest {
        assertFailsWith<NullPointerException> { convert(byteArrayOf(1, 2, 3)) }
        assertTrue(root.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun fileWriteFailurePropagatesWithoutReplacingTheExistingFile() = runTest {
        val blocker = root.resolve("upload").apply { writeText("original") }
        assertFailsWith<Exception> { convert(png) }
        assertEquals("original", blocker.readText())
        assertEquals(listOf(blocker), root.listFiles().orEmpty())
    }
    @Test
    fun managedRawImageBytesKeepMimeAndDisplayNameWithoutReencoding() = runTest {
        val bytes = byteArrayOf(1, 2, 3)
        val entity = filesManager.saveUploadFromBytes(bytes, "mcp_image.jpg", "image/jpeg")
        assertEquals("mcp_image.jpg", entity.displayName)
        assertEquals("image/jpeg", entity.mimeType)
        assertEquals(3L, entity.sizeBytes)
        assertEquals(entity, filesManager.get(entity.id))
        assertContentEquals(bytes, PlatformFile(filesManager.getFile(entity).toString()).readBytes())
        assertEquals(1 to 3L, filesManager.countChatFiles())
    }

    private suspend fun convert(bytes: ByteArray): String {
        val message = UIMessage.user("unused").copy(parts = listOf(
            UIMessagePart.Image("data:image/png;base64," + kotlin.io.encoding.Base64.encode(bytes)),
        ))
        return (filesManager.convertBase64ImagePartToLocalFile(message).parts.single() as UIMessagePart.Image).url
    }

}
