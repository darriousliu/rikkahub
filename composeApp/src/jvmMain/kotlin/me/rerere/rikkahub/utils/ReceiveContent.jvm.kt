@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package me.rerere.rikkahub.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.asAwtTransferable
import androidx.compose.ui.platform.awtClipboard
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.mimeType
import java.awt.Image
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import javax.swing.ImageIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

actual fun Modifier.onReceiveContent(
    onImage: (PlatformFile) -> Boolean,
    onText: (String) -> Boolean,
): Modifier = composed {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    imagePasteMenu(
        hasImageOnly = {
            val contents = clipboard.awtClipboard?.getContents(null)
            contents != null && contents.hasImages() && !contents.isDataFlavorSupported(DataFlavor.stringFlavor)
        },
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

@OptIn(ExperimentalComposeUiApi::class)
internal class ReceivingClipboard(
    private val clipboard: Clipboard,
    private val onImage: (PlatformFile) -> Boolean,
    private val onText: (String) -> Boolean,
) : Clipboard by clipboard {
    // CMP reads getClipEntry for the actual paste; menu availability uses nativeClipboard only.
    override suspend fun getClipEntry(): ClipEntry? {
        val entry = clipboard.getClipEntry() ?: return null
        val contents = entry.asAwtTransferable ?: return entry
        val files = contents.imageFiles()
        if (files.isNotEmpty()) {
            files.forEach { onImage(PlatformFile(it)) }
            return entry
        }
        if (contents.isDataFlavorSupported(DataFlavor.imageFlavor)) {
            val image = contents.getTransferData(DataFlavor.imageFlavor) as Image
            val file = withContext(Dispatchers.IO) {
                val bitmap = if (image is BufferedImage) image else {
                    val icon = ImageIcon(image)
                    BufferedImage(icon.iconWidth, icon.iconHeight, BufferedImage.TYPE_INT_ARGB).apply {
                        createGraphics().let { graphics ->
                            try { graphics.drawImage(image, 0, 0, null) } finally { graphics.dispose() }
                        }
                    }
                }
                Files.createTempFile("rikkahub-paste-", ".png").toFile().also {
                    ImageIO.write(bitmap, "png", it)
                }
            }
            try {
                onImage(PlatformFile(file))
            } finally {
                file.delete()
            }
            // Image MIME takes precedence; leave any accompanying text to the text field.
            return entry
        }
        if (contents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
            val text = contents.getTransferData(DataFlavor.stringFlavor) as? String
            if (text != null && onText(text)) return null
        }
        return entry
    }
}

private fun Transferable.imageFiles(): List<File> =
    if (isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
        (getTransferData(DataFlavor.javaFileListFlavor) as List<*>).filterIsInstance<File>()
            .filter { PlatformFile(it).mimeType()?.toString()?.startsWith("image/") == true }
    } else emptyList()

private fun Transferable.hasImages(): Boolean =
    isDataFlavorSupported(DataFlavor.imageFlavor) || imageFiles().isNotEmpty()
