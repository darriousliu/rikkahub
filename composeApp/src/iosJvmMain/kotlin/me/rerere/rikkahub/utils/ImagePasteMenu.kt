package me.rerere.rikkahub.utils

import androidx.compose.foundation.text.contextmenu.builder.item
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuKeys
import androidx.compose.foundation.text.contextmenu.modifier.appendTextContextMenuComponents
import androidx.compose.foundation.text.contextmenu.modifier.filterTextContextMenuComponents
import androidx.compose.ui.Modifier

private object ImagePasteKey

// CMP's default Paste item is disabled when the clipboard contains only an image.
internal fun Modifier.imagePasteMenu(
    hasImageOnly: () -> Boolean,
    onPaste: () -> Unit,
): Modifier = filterTextContextMenuComponents {
    it.key != TextContextMenuKeys.PasteKey || !hasImageOnly()
}.appendTextContextMenuComponents {
    if (hasImageOnly()) {
        item(key = ImagePasteKey, label = "Paste") {
            onPaste()
            close()
        }
    }
}
