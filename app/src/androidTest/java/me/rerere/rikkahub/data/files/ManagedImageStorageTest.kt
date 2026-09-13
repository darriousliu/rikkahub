package me.rerere.rikkahub.data.files

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.system.Os
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDateTime
import kotlinx.io.files.Path
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.TokenUsage
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageAnnotation
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.AppDatabaseConstructor
import me.rerere.rikkahub.data.db.buildAppDatabase
import me.rerere.rikkahub.data.db.entity.ManagedFileEntity
import me.rerere.rikkahub.data.db.fts.MessageFtsDialect
import me.rerere.rikkahub.data.repository.FilesRepository
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.io.encoding.Base64
import kotlin.uuid.Uuid

@RunWith(AndroidJUnit4::class)
class ManagedImageStorageTest {
    private val scopeJob = SupervisorJob()
    private val scope = CoroutineScope(scopeJob + Dispatchers.IO)
    private lateinit var root: File
    private lateinit var database: AppDatabase
    private lateinit var repository: FilesRepository
    private lateinit var filesManager: FilesManager

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        root = Files.createTempDirectory(context.cacheDir.toPath(), "cmp60-managed-image-").toFile()
        database = buildAppDatabase(
            builder = Room.inMemoryDatabaseBuilder<AppDatabase>(
                context = context,
                factory = AppDatabaseConstructor::initialize,
            ),
            driver = BundledSQLiteDriver(),
            ftsDialect = MessageFtsDialect.UNICODE61,
        )
        repository = FilesRepository(database.managedFileDao())
        filesManager = FilesManager(Path(root.absolutePath), repository, scope, asyncFileIo = false)
    }

    @After
    fun tearDown() = runBlocking {
        try {
            withTimeout(WAIT_MILLIS) { scopeJob.cancelAndJoin() }
        } finally {
            try {
                if (::database.isInitialized) database.close()
            } finally {
                if (::root.isInitialized) {
                    assertTrue("Only the unique CMP60 cache directory must be removed", root.deleteRecursively())
                }
            }
        }
    }

    @Test
    fun jpegMessageImageBecomesReadablePngAndPreservesMetadataAndOtherParts() = runBlocking {
        val jpeg = jpegBytes()
        val metadata = JsonObject(mapOf("cmp60" to JsonPrimitive("preserved")))
        val image = inlineJpeg(jpeg).copy(metadata = metadata)
        val text = UIMessagePart.Text("data:image/jpeg;base64,this is still text", metadata)
        val document = UIMessagePart.Document(
            url = "data:application/octet-stream;base64,AQID",
            fileName = "unchanged.bin",
            mime = "application/octet-stream",
            metadata = metadata,
        )
        val existingImage = UIMessagePart.Image("file:///unchanged.png", metadata)
        val reasoning = UIMessagePart.Reasoning("Unchanged reasoning", metadata = metadata)
        val input = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(text, image, document, existingImage, reasoning),
            annotations = listOf(UIMessageAnnotation.UrlCitation("CMP60", "https://example.invalid/cmp60")),
            createdAt = LocalDateTime(2026, 1, 2, 3, 4, 5),
            finishedAt = LocalDateTime(2026, 1, 2, 3, 4, 6),
            modelId = Uuid.random(),
            usage = TokenUsage(promptTokens = 7, completionTokens = 11),
            translation = "Preserved translation",
        )

        val output = filesManager.convertBase64ImagePartToLocalFile(input)
        val converted = output.parts[1] as UIMessagePart.Image
        assertEquals("All message fields except parts must survive", input, output.copy(parts = input.parts))
        assertEquals(image.copy(url = converted.url), converted)
        assertSame(metadata, converted.metadata)
        listOf(0, 2, 3, 4).forEach { index -> assertSame(input.parts[index], output.parts[index]) }
        assertEquals(
            "The input message must remain unchanged",
            listOf(text, image, document, existingImage, reasoning),
            input.parts,
        )
        assertTrue(image.url.startsWith("data:image/jpeg;base64,"))

        val file = storedFile(converted.url)
        val png = file.readBytes()
        assertArrayEquals(PNG_SIGNATURE, png.take(PNG_SIGNATURE.size).toByteArray())
        // JPEG is lossy: compare against its decoded pixels, not the pre-compression bitmap.
        withDecodedBitmap(jpeg) { expected ->
            withDecodedBitmap(png) { actual ->
                assertEquals(IMAGE_WIDTH, actual.width)
                assertEquals(IMAGE_HEIGHT, actual.height)
                assertEquals(expected.width, actual.width)
                assertEquals(expected.height, actual.height)
                assertArrayEquals(pixels(expected), pixels(actual))
            }
        }
        assertEquals("upload/${file.name}", awaitFiles(1).single().relativePath)
    }

    @Test
    fun convertedImageRegistersOriginalPngMetadataAndCountAndDeletionMatchDisk() = runBlocking {
        assertEquals(0 to 0L, filesManager.countChatFiles())
        val input = UIMessage(role = MessageRole.ASSISTANT, parts = listOf(inlineJpeg(jpegBytes())))

        val output = filesManager.convertBase64ImagePartToLocalFile(input)
        val url = (output.parts.single() as UIMessagePart.Image).url
        val file = storedFile(url)
        val registered = awaitFiles(1).single()
        assertTrue("Room must assign an id to the managed file", registered.id > 0)
        assertEquals(FileFolders.UPLOAD, registered.folder)
        assertEquals("upload/${file.name}", registered.relativePath)
        assertEquals("image.png", registered.displayName)
        assertEquals("image/png", registered.mimeType)
        assertEquals(file.readBytes().size.toLong(), registered.sizeBytes)
        assertEquals(registered, repository.getByPath(registered.relativePath))
        assertEquals(registered, repository.getById(registered.id))
        assertEquals(file.canonicalPath, File(filesManager.getFile(registered).toString()).canonicalPath)
        assertEquals(1 to registered.sizeBytes, filesManager.countChatFiles())

        // Registration must finish before deletion; both database operations use the real IO dispatcher.
        filesManager.deleteChatFiles(listOf(url))
        assertFalse("Android must delete the file before returning with asyncFileIo=false", file.exists())
        awaitFiles(0)
        assertNull(repository.getByPath(registered.relativePath))
        assertNull(repository.getById(registered.id))
        assertEquals(0 to 0L, filesManager.countChatFiles())
        assertTrue(requireNotNull(File(root, FileFolders.UPLOAD).listFiles()).isEmpty())
    }

    @Test
    fun invalidImageAndBlockedUploadPropagateFailuresWithoutChangingTheBlocker() = runBlocking {
        val invalid = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(inlineJpeg("CMP60 is not an image".encodeToByteArray())),
        )
        val decodeFailure = runCatching { filesManager.convertBase64ImagePartToLocalFile(invalid) }.exceptionOrNull()
        assertTrue(
            "Undecodable image must propagate the original null-decode failure: $decodeFailure",
            decodeFailure is NullPointerException,
        )
        assertFalse(File(root, FileFolders.UPLOAD).exists())
        assertEquals(0 to 0L, filesManager.countChatFiles())
        withTimeout(WAIT_MILLIS) { scopeJob.children.toList().joinAll() }
        assertTrue(repository.listByFolder(FileFolders.UPLOAD).first().isEmpty())

        // A regular file at upload makes child writes fail reliably without depending on emulator privileges.
        val blocker = File(root, FileFolders.UPLOAD)
        val originalBytes = "CMP60 upload blocker must not be overwritten".encodeToByteArray()
        blocker.writeBytes(originalBytes)
        val originalMode = Os.stat(blocker.absolutePath).st_mode
        val originalModified = blocker.lastModified()
        val input = UIMessage(role = MessageRole.ASSISTANT, parts = listOf(inlineJpeg(jpegBytes())))

        val writeFailure = runCatching { filesManager.convertBase64ImagePartToLocalFile(input) }.exceptionOrNull()
        assertTrue("The upload write error must propagate to the caller: $writeFailure", writeFailure is IOException)
        assertTrue(blocker.isFile)
        assertArrayEquals(originalBytes, blocker.readBytes())
        assertEquals(originalMode, Os.stat(blocker.absolutePath).st_mode)
        assertEquals(originalModified, blocker.lastModified())
        assertEquals(listOf(blocker.name), requireNotNull(root.listFiles()).map { it.name })
        withTimeout(WAIT_MILLIS) { scopeJob.children.toList().joinAll() }
        assertTrue(repository.listByFolder(FileFolders.UPLOAD).first().isEmpty())
    }

    @Test
    fun mcpImageBytesStayJpegAndMetadataIsQueryableWhenSaveReturns() = runBlocking {
        val jpeg = jpegBytes()

        val registered = filesManager.saveUploadFromBytes(jpeg, "mcp_image.jpg", "image/jpeg")

        // This API returns only after insertion; do not poll or wait for AppScope registration here.
        assertEquals(registered, filesManager.get(registered.id))
        assertEquals(registered, filesManager.getByRelativePath(registered.relativePath))
        val file = File(filesManager.getFile(registered).toString())
        assertArrayEquals("MCP bytes must be stored without re-encoding", jpeg, file.readBytes())
        assertEquals(File(root, FileFolders.UPLOAD).canonicalFile, file.parentFile?.canonicalFile)
        assertEquals("jpg", file.extension)
        assertTrue(registered.id > 0)
        assertEquals(FileFolders.UPLOAD, registered.folder)
        assertEquals("upload/${file.name}", registered.relativePath)
        assertEquals("mcp_image.jpg", registered.displayName)
        assertEquals("image/jpeg", registered.mimeType)
        assertEquals(jpeg.size.toLong(), registered.sizeBytes)
    }

    private suspend fun awaitFiles(count: Int): List<ManagedFileEntity> = withTimeout(WAIT_MILLIS) {
        repository.listByFolder(FileFolders.UPLOAD).first { it.size == count }
    }

    private fun storedFile(url: String): File {
        val uri = URI(url)
        assertEquals("file", uri.scheme)
        return File(uri).also { file ->
            assertTrue("Converted PNG must be readable", file.isFile && file.canRead())
            assertEquals(File(root, FileFolders.UPLOAD).canonicalFile, file.parentFile?.canonicalFile)
            assertEquals("png", file.extension)
            assertEquals(UUID.fromString(file.nameWithoutExtension).toString(), file.nameWithoutExtension)
        }
    }

    private fun inlineJpeg(bytes: ByteArray) = UIMessagePart.Image("data:image/jpeg;base64,${Base64.encode(bytes)}")

    private fun jpegBytes(): ByteArray {
        val bitmap = Bitmap.createBitmap(IMAGE_WIDTH, IMAGE_HEIGHT, Bitmap.Config.ARGB_8888)
        try {
            for (y in 0 until IMAGE_HEIGHT) {
                for (x in 0 until IMAGE_WIDTH) {
                    bitmap.setPixel(x, y, Color.rgb(x * 31, y * 47, (x * 19 + y * 29) % 256))
                }
            }
            return ByteArrayOutputStream().use { output ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output))
                output.toByteArray().also { bytes ->
                    assertArrayEquals(byteArrayOf(0xff.toByte(), 0xd8.toByte()), bytes.take(2).toByteArray())
                }
            }
        } finally {
            bitmap.recycle()
        }
    }

    private inline fun withDecodedBitmap(bytes: ByteArray, block: (Bitmap) -> Unit) {
        val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)) { "Bitmap decode failed" }
        try {
            block(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    private fun pixels(bitmap: Bitmap): IntArray = IntArray(bitmap.width * bitmap.height).also {
        bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
        const val IMAGE_WIDTH = 8
        const val IMAGE_HEIGHT = 5
        val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
    }
}
