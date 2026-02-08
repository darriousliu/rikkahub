package me.rerere.common.utils

import coil3.Uri
import coil3.toUri
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.absolutePath
import me.rerere.common.PlatformContext
import platform.Foundation.NSFileManager

actual fun getUriForFile(
    context: PlatformContext,
    file: PlatformFile
): Uri {
    return file.absolutePath().toUri()
}

actual fun PlatformFile.deleteRecursively(): Boolean {
    val nsUrl = this.nsUrl
    return try {
        val fileManager = NSFileManager.defaultManager
        fileManager.removeItemAtURL(nsUrl, null)
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

actual fun PlatformFile.toUri(context: PlatformContext): Uri {
    return this.absolutePath().toUri()
}

actual fun Uri.toFile(): PlatformFile {
    val path = this.toString()
    return PlatformFile(path)
}

actual fun PlatformFile.canRead(): Boolean {
    val path = this.nsUrl.path ?: return false
    return NSFileManager.defaultManager.isReadableFileAtPath(path)
}

actual fun writeContentToUri(
    context: PlatformContext,
    uri: Uri,
    content: ByteArray
) {
}

actual fun readContentFromUri(context: PlatformContext, uri: Uri): ByteArray {
    TODO("Not yet implemented")
}

