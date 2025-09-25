package me.rerere.rikkahub.utils

import coil3.BitmapImage
import coil3.Uri
import io.github.vinceglb.filekit.PlatformFile
import me.rerere.ai.ui.UIMessage
import me.rerere.common.PlatformContext
import kotlin.io.encoding.ExperimentalEncodingApi

private const val TAG = "ChatUtil"

actual fun PlatformContext.copyMessageToClipboard(message: UIMessage) {
}

actual fun ByteArray.toImage(): BitmapImage {
    TODO("Not yet implemented")
}

actual fun BitmapImage.compress(): ByteArray {
    TODO("Not yet implemented")
}


actual fun PlatformContext.createChatFilesByByteArrays(byteArrays: List<ByteArray>): List<Uri> {
    TODO("Not yet implemented")
}

actual fun PlatformContext.getFileNameFromUri(uri: Uri): String? {
    TODO("Not yet implemented")
}

actual fun PlatformContext.getFileMimeType(uri: Uri): String? {
    TODO("Not yet implemented")
}

@OptIn(markerClass = [ExperimentalEncodingApi::class])
actual suspend fun PlatformContext.convertBase64ImagePartToLocalFile(message: UIMessage): UIMessage {
    TODO("Not yet implemented")
}

actual fun PlatformContext.deleteChatFiles(uris: List<Uri>) {
}

actual fun PlatformContext.deleteAllChatFiles() {
}

actual suspend fun PlatformContext.countChatFiles(): Pair<Int, Long> {
    TODO("Not yet implemented")
}

actual fun PlatformContext.getImagesDir(): PlatformFile {
    TODO("Not yet implemented")
}

actual fun PlatformContext.createImageFileFromBase64(
    base64Data: String,
    filePath: String
): PlatformFile {
    TODO("Not yet implemented")
}
