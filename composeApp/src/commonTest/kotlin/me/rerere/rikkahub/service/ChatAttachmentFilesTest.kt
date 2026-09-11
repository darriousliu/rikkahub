package me.rerere.rikkahub.service

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.io.files.Path
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.io.IOException
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.files.buildUuidFileName
import me.rerere.rikkahub.platform.FileKitPlatformFileStore
import me.rerere.rikkahub.utils.canonicalFile
import me.rerere.rikkahub.utils.deleteRecursively
import me.rerere.rikkahub.utils.exists
import me.rerere.rikkahub.utils.mkdirs
import me.rerere.rikkahub.utils.listFiles
import me.rerere.rikkahub.utils.length
import me.rerere.rikkahub.utils.resolve
import me.rerere.rikkahub.utils.writeBytes
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class ChatAttachmentFilesTest {
    private val root = Path(SystemTemporaryDirectory, "cmp-attachment-test-${Uuid.random()}")
        .canonicalFile.apply { mkdirs() }
    private val store = FileKitPlatformFileStore(PlatformFile(root.toString()))
    private val attachments = SharedChatAttachmentStore(store)

    @AfterTest
    fun cleanUp() { root.deleteRecursively() }

    @Test
    fun copiedFilesUseOriginalUploadDirectoryAndUuidExtension() = runTest {
        val source = source("中文 name.PDF", byteArrayOf(0, 1, 2, -1))
        val first = store.copyIntoSandbox(source).getOrThrow()
        val second = store.copyIntoSandbox(source).getOrThrow()
        assertEquals(root.resolve("upload").toString(), Path(first.path).parent.toString())
        assertEquals("pdf", first.name.substringAfterLast('.'))
        Uuid.parse(first.name.substringBeforeLast('.'))
        assertNotEquals(first.path, second.path)
        assertContentEquals(source.readBytes(), first.readBytes())
        assertContentEquals(source.readBytes(), second.readBytes())
    }

    @Test
    fun generatedImagesUseTheSameUploadDirectory() = runTest {
        val bytes = byteArrayOf(8, 6, 4, 2)
        val stored = store.writeIntoSandbox(bytes, "image.png")
        assertEquals(root.resolve("upload").toString(), Path(stored.path).parent.toString())
        assertEquals("png", stored.name.substringAfterLast('.'))
        Uuid.parse(stored.name.substringBeforeLast('.'))
        assertContentEquals(bytes, stored.readBytes())
    }

    @Test
    fun uuidNamesPreserveTheOriginalExtensionPriorityAndFallbacks() {
        val cases = listOf(
            Triple("photo.JPEG", "image/png", "jpeg"),
            Triple("archive.tar.GZ", null, "gz"),
            Triple(".hidden", null, "hidden"),
            Triple("trailing.", null, "bin"),
            Triple("no-extension", "APPLICATION/PDF", "pdf"),
            Triple(null, "image/png", "png"),
            Triple("", "application/x-unknown-cmp", "bin"),
            Triple(null, null, "bin"),
        )
        cases.forEach { (displayName, mime, extension) ->
            val name = buildUuidFileName(displayName, mime)
            assertEquals(extension, name.substringAfterLast('.'))
            Uuid.parse(name.substringBeforeLast('.'))
        }
    }

    @Test
    fun importRetainsDisplayNamesAndMediaKinds() = runTest {
        val sources = listOf("照片.PNG", "movie.mp4", "song.mp3", "中文 note.txt")
            .map { source(it, byteArrayOf(1, 3, 5)) }
        val imported = attachments.import(sources)
        assertIs<UIMessagePart.Image>(imported[0])
        assertIs<UIMessagePart.Video>(imported[1])
        assertIs<UIMessagePart.Audio>(imported[2])
        val document = assertIs<UIMessagePart.Document>(imported[3])
        assertEquals("中文 note.txt", document.fileName)
        assertEquals("text/plain", document.mime)
        assertContentEquals(sources.last().readBytes(), PlatformFile(document.url.toLocalFilePath()).readBytes())
    }

    @Test
    fun missingSourceDoesNotDiscardTheOtherImportedFiles() = runTest {
        val valid = source("good.txt", byteArrayOf(1))
        val imported = attachments.import(listOf(PlatformFile(root.resolve("missing.txt").toString()), valid))
        assertEquals("good.txt", assertIs<UIMessagePart.Document>(imported.single()).fileName)
    }

    @Test
    fun copyFailureLeavesTheCreatedFileAsInTheOriginalFilesManager() = runTest {
        val result = store.copyIntoSandbox(PlatformFile(root.resolve("missing.txt").toString()))
        assertTrue(result.isFailure)
        val file = root.resolve("upload").listFiles().orEmpty().single()
        assertEquals(0L, file.length())
    }

    @Test
    fun unwritableUploadPathKeepsCopyFailureAndByteWriteExceptionDistinct() = runTest {
        val valid = source("source.txt", byteArrayOf(7))
        root.resolve("upload").writeBytes(byteArrayOf(1))
        assertTrue(store.copyIntoSandbox(valid).isFailure)
        assertFailsWith<IOException> { store.writeIntoSandbox(byteArrayOf(2), "image.png") }
    }

    @Test
    fun cancellationKeepsCopyFailureAndByteWriteExceptionDistinct() = runTest {
        val valid = source("source.txt", byteArrayOf(7))
        var copyFailure: Throwable? = null
        launch {
            currentCoroutineContext().cancel()
            copyFailure = store.copyIntoSandbox(valid).exceptionOrNull()
        }.join()
        assertIs<CancellationException>(copyFailure)
        launch {
            currentCoroutineContext().cancel()
            assertFailsWith<CancellationException> { store.writeIntoSandbox(byteArrayOf(2), "image.png") }
        }.join()
    }

    @Test
    fun fileUriDecodingPreservesUnicodeSpacePlusPercentAndHash() {
        val file = source("中文 + % #.txt", byteArrayOf(1))
        assertEquals(file.path, file.toFileUri().toLocalFilePath())
        assertEquals(file.path, file.toFileUri().replace("file://", "file:").toLocalFilePath())
    }

    @Test
    fun deletionOnlyTargetsRequestedLocalFilesAndToleratesMissingFiles() = runTest {
        val selected = source("selected.txt", byteArrayOf(1))
        val other = source("other.txt", byteArrayOf(2))
        attachments.delete(listOf(selected.toFileUri(), "https://example.invalid/other.txt", "data:text/plain,other"))
        assertFalse(Path(selected.path).exists())
        assertTrue(Path(other.path).exists())
        attachments.delete(listOf(selected.toFileUri()))
    }

    @Test
    fun oldDirectoryFilesCanStillBeReadAndImportedWithoutMovingTheOriginal() = runTest {
        val legacyDirectory = root.resolve("platform-files/attachments").apply { mkdirs() }
        val legacy = legacyDirectory.resolve("old name +.txt").apply { writeBytes(byteArrayOf(9, 8, 7)) }
        val imported = assertIs<UIMessagePart.Document>(
            attachments.importLocations(listOf(PlatformFile(legacy.toString()).toFileUri())).single(),
        )
        assertTrue(legacy.exists())
        assertNotEquals(legacy.toString(), imported.url.toLocalFilePath())
        assertContentEquals(byteArrayOf(9, 8, 7), PlatformFile(imported.url.toLocalFilePath()).readBytes())
    }

    private fun source(name: String, bytes: ByteArray): PlatformFile {
        val directory = root.resolve("sources").apply { mkdirs() }
        return PlatformFile(directory.resolve(name).apply { writeBytes(bytes) }.toString())
    }
}
