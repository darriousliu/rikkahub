@file:OptIn(
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlinx.cinterop.BetaInteropApi::class,
)

package me.rerere.rikkahub.utils

import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import io.github.vinceglb.filekit.path
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.memScoped
import kotlinx.coroutines.runBlocking
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIPasteboard
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ReceiveContentTest {
    @Test
    fun originalImageBytesAndMultipleItemsAreImportedWithoutConsumingMixedText() = runBlocking {
        val board = UIPasteboard.pasteboardWithUniqueName()
        try {
            val png = Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGNQSFjwHwAD5AIge4YO3AAAAABJRU5ErkJggg==")
            val gif = Base64.decode("R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7")
            board.items = listOf(
                mapOf("public.png" to png.toData()),
                mapOf("com.compuserve.gif" to gif.toData()),
                mapOf("public.utf8-plain-text" to "mixed long text".repeat(50)),
            )
            val files = mutableListOf<Path>()
            val copied = mutableListOf<ByteArray>()
            val clipboard = ReceivingClipboard(TestClipboard(board), {
                val file = Path(it.path)
                files += file
                copied += SystemFileSystem.source(file).buffered().use { it.readByteArray() }
                true
            }, { error("Images take precedence; accompanying text must stay inline") })
            assertTrue(clipboard.nativeClipboard.hasImages)
            assertTrue(files.isEmpty(), "Checking availability must not import anything")
            assertEquals("mixed long text".repeat(50), clipboard.getClipEntry()?.getPlainText())
            assertEquals(2, copied.size)
            assertContentEquals(png, copied[0])
            assertContentEquals(gif, copied[1])
            assertTrue(files.all { !it.exists() })
        } finally {
            UIPasteboard.removePasteboardWithName(board.name)
        }
    }

    @Test
    fun textConsumptionAndCopyKeepUsingTheProvidedPasteboard() = runBlocking {
        val board = UIPasteboard.pasteboardWithUniqueName()
        try {
            board.string = "original text"
            val received = mutableListOf<String>()
            var consume = false
            val clipboard = ReceivingClipboard(TestClipboard(board), { error("unexpected image") }) {
                received += it
                consume
            }
            assertSame(board, clipboard.nativeClipboard)
            assertTrue(received.isEmpty())
            assertEquals("original text", clipboard.getClipEntry()?.getPlainText())
            consume = true
            assertNull(clipboard.getClipEntry())
            assertEquals(listOf("original text", "original text"), received)
            clipboard.setClipEntry(ClipEntry.withPlainText("copy result"))
            assertEquals("copy result", board.string)
        } finally {
            UIPasteboard.removePasteboardWithName(board.name)
        }
    }

    @Test
    fun imageTemporaryFileIsRemovedOnReceiverFailure() = runBlocking {
        val board = UIPasteboard.pasteboardWithUniqueName()
        try {
            val bytes = Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGNQSFjwHwAD5AIge4YO3AAAAABJRU5ErkJggg==")
            board.items = listOf(mapOf("public.png" to bytes.toData()))
            var received: Path? = null
            val failure = IllegalStateException("receiver failure")
            val clipboard = ReceivingClipboard(TestClipboard(board), {
                received = Path(it.path)
                throw failure
            }, { false })
            assertSame(failure, runCatching { clipboard.getClipEntry() }.exceptionOrNull())
            assertFalse(received!!.exists())
        } finally {
            UIPasteboard.removePasteboardWithName(board.name)
        }
    }

    private fun ByteArray.toData(): NSData = memScoped {
        NSData.create(bytes = allocArrayOf(this@toData), length = size.toULong())
    }

    private class TestClipboard(override val nativeClipboard: UIPasteboard) : Clipboard {
        override suspend fun getClipEntry(): ClipEntry? = nativeClipboard.string?.let(ClipEntry::withPlainText)
        override suspend fun setClipEntry(clipEntry: ClipEntry?) { nativeClipboard.string = clipEntry?.getPlainText() }
    }
}
