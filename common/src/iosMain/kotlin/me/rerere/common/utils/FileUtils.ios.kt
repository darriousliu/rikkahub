package me.rerere.common.utils

import coil3.Uri
import io.github.vinceglb.filekit.PlatformFile
import me.rerere.common.PlatformContext
import platform.Foundation.NSFileManager

actual fun getUriForFile(
    context: PlatformContext,
    file: PlatformFile
): Uri {
    TODO("Not yet implemented")
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
