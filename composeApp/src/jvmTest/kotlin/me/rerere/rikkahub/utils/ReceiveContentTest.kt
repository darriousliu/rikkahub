@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package me.rerere.rikkahub.utils

import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.NativeClipboard
import androidx.compose.ui.platform.asAwtTransferable
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.image.BufferedImage
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ReceiveContentTest {
    @Test
    fun textIsConsumedOnlyWhenRequestedAndCopyStillUsesTheNativeClipboard() = runBlocking {
        val original = TestClipboard(StringSelection("pasted text"))
        var consume = false
        val received = mutableListOf<String>()
        val clipboard = ReceivingClipboard(original, { error("unexpected image") }) {
            received += it
            consume
        }
        assertTrue(received.isEmpty())
        assertSame(original.nativeClipboard, clipboard.nativeClipboard)
        assertTrue(received.isEmpty(), "Checking native menu availability must not paste")
        assertSame(original.entry, clipboard.getClipEntry())
        consume = true
        assertNull(clipboard.getClipEntry())
        assertEquals(listOf("pasted text", "pasted text"), received)
        val copied = ClipEntry(StringSelection("copied"))
        clipboard.setClipEntry(copied)
        assertSame(copied, original.entry)
    }

    @Test
    fun imagePixelsAreTransferredOnceAndMixedTextKeepsTheOriginalImagePrecedence() = runBlocking {
        val bitmap = BufferedImage(3, 2, BufferedImage.TYPE_INT_ARGB).apply { setRGB(1, 1, 0xff1256ab.toInt()) }
        val source = TestClipboard(Contents(DataFlavor.imageFlavor to bitmap, DataFlavor.stringFlavor to "long".repeat(50)))
        var copied: java.io.File? = null
        var count = 0
        val clipboard = ReceivingClipboard(source, { image ->
            copied = image.file
            count++
            val decoded = ImageIO.read(image.file)
            assertEquals(3, decoded.width)
            assertEquals(2, decoded.height)
            assertEquals(bitmap.getRGB(1, 1), decoded.getRGB(1, 1))
            true
        }, { error("Mixed image/text must leave text inline, not convert it to a file") })
        assertSame(source.entry, clipboard.getClipEntry())
        assertEquals(1, count)
        assertFalse(copied!!.exists(), "The native image's temporary PNG must be removed after import")
        assertEquals("long".repeat(50), source.entry!!.asAwtTransferable!!.getTransferData(DataFlavor.stringFlavor))
    }

    @Test
    fun copiedImageFilesKeepOriginalBytesAndAreNotDeleted() = runBlocking {
        val source = Files.createTempFile("receive-content-test-", ".gif").toFile()
        try {
            val bytes = "GIF89a original file payload".encodeToByteArray()
            source.writeBytes(bytes)
            val original = TestClipboard(Contents(DataFlavor.javaFileListFlavor to listOf(source)))
            var count = 0
            val clipboard = ReceivingClipboard(original, {
                count++
                assertContentEquals(bytes, it.file.readBytes())
                true
            }, { error("unexpected text") })
            clipboard.getClipEntry()
            assertEquals(1, count)
            assertTrue(source.exists())
            assertContentEquals(bytes, source.readBytes())
        } finally {
            source.delete()
        }
    }

    @Test
    fun temporaryImageIsRemovedEvenIfTheReceiverThrows() = runBlocking {
        val original = TestClipboard(Contents(DataFlavor.imageFlavor to BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB)))
        var file: java.io.File? = null
        val failure = IllegalStateException("receiver failed")
        val clipboard = ReceivingClipboard(original, {
            file = it.file
            throw failure
        }, { false })
        val result = runCatching { clipboard.getClipEntry() }
        assertSame(failure, result.exceptionOrNull())
        assertFalse(file!!.exists())
    }

    private class TestClipboard(value: Transferable) : Clipboard {
        var entry: ClipEntry? = ClipEntry(value)
        override val nativeClipboard: NativeClipboard = java.awt.datatransfer.Clipboard("test").apply {
            setContents(value, null)
        }
        override suspend fun getClipEntry(): ClipEntry? = entry
        override suspend fun setClipEntry(clipEntry: ClipEntry?) { entry = clipEntry }
    }

    private class Contents(vararg values: Pair<DataFlavor, Any>) : Transferable {
        private val values = values.toMap()
        override fun getTransferDataFlavors(): Array<DataFlavor> = values.keys.toTypedArray()
        override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor in values
        override fun getTransferData(flavor: DataFlavor): Any = values[flavor] ?: throw UnsupportedFlavorException(flavor)
    }
}
