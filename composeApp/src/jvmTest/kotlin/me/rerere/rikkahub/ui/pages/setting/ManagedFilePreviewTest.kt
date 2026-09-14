package me.rerere.rikkahub.ui.pages.setting

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import me.rerere.rikkahub.data.files.FileFolders
import me.rerere.rikkahub.data.files.testFilesManager
import me.rerere.rikkahub.data.files.toFileUri
import me.rerere.rikkahub.generated.resources.Res
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ManagedFilePreviewTest {
    @Test
    fun managedImageLoadsFromAnEscapedPathAndDeletionRemovesItsFileAndRecord() = runTest {
        val root = Files.createTempDirectory("CMP66 中文 + %# ").toFile()
        val scope = CoroutineScope(SupervisorJob())
        val manager = testFilesManager(Path(root.path), scope)
        val loader = ImageLoader.Builder(PlatformContext.INSTANCE).build()
        try {
            val bytes = Res.readBytes("files/icons/bing.png")
            val file = manager.saveManagedFromBytes(FileFolders.UPLOAD, bytes, "图片 + %#.png", "image/png")
            assertEquals("图片 + %#.png", manager.list().single().displayName)
            assertEquals(bytes.size.toLong(), file.sizeBytes)
            val result = loader.execute(
                ImageRequest.Builder(PlatformContext.INSTANCE)
                    .data(manager.getFile(file).toFileUri())
                    .build()
            )
            val image = assertIs<SuccessResult>(result).image
            assertTrue(image.width > 0 && image.height > 0)

            assertTrue(manager.delete(file.id, deleteFromDisk = true))
            assertTrue(manager.list().isEmpty())
            assertFalse(java.io.File(manager.getFile(file).toString()).exists())
        } finally {
            loader.shutdown()
            scope.cancel()
            root.deleteRecursively()
        }
    }
}
