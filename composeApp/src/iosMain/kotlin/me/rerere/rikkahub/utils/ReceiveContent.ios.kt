@file:OptIn(
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package me.rerere.rikkahub.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.cacheDir
import io.github.vinceglb.filekit.delete
import io.github.vinceglb.filekit.div
import io.github.vinceglb.filekit.write
import kotlinx.cinterop.readBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSIndexSet
import platform.UIKit.UIPasteboardTypeListImage
import platform.UniformTypeIdentifiers.UTType
import kotlin.uuid.Uuid

actual fun Modifier.onReceiveContent(
    onImage: (PlatformFile) -> Boolean,
    onText: (String) -> Boolean,
): Modifier = composed {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    imagePasteMenu(
        hasImageOnly = { clipboard.nativeClipboard.hasImages && !clipboard.nativeClipboard.hasStrings },
        onPaste = { scope.launch { clipboard.getClipEntry() } },
    )
}

@Composable
actual fun rememberReceiveContentClipboard(
    onImage: (PlatformFile) -> Boolean,
    onText: (String) -> Boolean,
): Clipboard {
    val clipboard = LocalClipboard.current
    return remember(clipboard, onImage, onText) { ReceivingClipboard(clipboard, onImage, onText) }
}

internal class ReceivingClipboard(
    private val clipboard: Clipboard,
    private val onImage: (PlatformFile) -> Boolean,
    private val onText: (String) -> Boolean,
) : Clipboard by clipboard {
    // CMP checks native pasteboard flags for menu availability; reading happens only on paste.
    override suspend fun getClipEntry(): ClipEntry? {
        val pasteboard = nativeClipboard
        if (pasteboard.hasImages) {
            val remainingText = mutableListOf<String>()
            repeat(pasteboard.numberOfItems.toInt()) { index ->
                val itemSet = NSIndexSet.indexSetWithIndex(index.toULong())
                val types = pasteboard.pasteboardTypesForItemSet(itemSet)?.firstOrNull() as? List<*>
                val imageType = types?.filterIsInstance<String>()?.firstOrNull {
                    it in UIPasteboardTypeListImage
                }
                var consumed = false
                if (imageType != null) {
                    val data = pasteboard.dataForPasteboardType(imageType, itemSet)?.firstOrNull() as? NSData
                    if (data != null) {
                        val extension = UTType.typeWithIdentifier(imageType)?.preferredFilenameExtension ?: "img"
                        val file = FileKit.cacheDir / "paste-${Uuid.random()}.$extension"
                        try {
                            withContext(Dispatchers.IO) {
                                file.write(data.bytes!!.readBytes(data.length.toInt()))
                            }
                            consumed = onImage(file)
                        } finally {
                            file.delete()
                        }
                    }
                }
                if (!consumed) {
                    pasteboard.valuesForPasteboardType("public.utf8-plain-text", itemSet)
                        ?.filterIsInstance<String>()?.let(remainingText::addAll)
                }
            }
            // Image MIME takes precedence; only text from unconsumed items remains inline.
            return remainingText.takeIf { it.isNotEmpty() }?.let { ClipEntry.withPlainText(it.joinToString("\n")) }
        }
        val entry = clipboard.getClipEntry()
        val text = entry?.getPlainText()
        return if (text != null && onText(text)) null else entry
    }
}
