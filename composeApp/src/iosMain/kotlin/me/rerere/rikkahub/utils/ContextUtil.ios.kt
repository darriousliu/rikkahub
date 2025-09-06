package me.rerere.rikkahub.utils

import coil3.BitmapImage
import io.github.vinceglb.filekit.PlatformFile
import me.rerere.common.PlatformContext

actual fun PlatformContext.readClipboardText(): String {
    TODO("Not yet implemented")
}

actual fun PlatformContext.joinQQGroup(key: String?): Boolean {
    TODO("Not yet implemented")
}

actual fun PlatformContext.writeClipboardText(text: String) {
}

actual fun PlatformContext.openUrl(url: String) {
}

actual fun PlatformContext.exportImage(
    activity: PlatformContext,
    bitmap: BitmapImage,
    fileName: String
) {
}

actual fun PlatformContext.exportImageFile(
    activity: PlatformContext,
    file: PlatformFile,
    fileName: String
) {
}
