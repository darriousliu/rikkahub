@file:Suppress("NOTHING_TO_INLINE")

package me.rerere.common.utils

import androidx.core.content.FileProvider
import androidx.core.net.toFile
import androidx.core.net.toUri
import coil3.Uri
import coil3.toAndroidUri
import coil3.toCoilUri
import io.github.vinceglb.filekit.AndroidFile
import io.github.vinceglb.filekit.PlatformFile
import me.rerere.common.PlatformContext
import java.io.File
import kotlin.io.deleteRecursively
import kotlin.io.readBytes
import kotlin.use

inline fun File.toPlatformFile() = PlatformFile(this)

inline fun PlatformFile.toFile() = androidFile.let {
    when (it) {
        is AndroidFile.FileWrapper -> it.file
        is AndroidFile.UriWrapper -> it.uri.toFile()
    }
}

actual fun getUriForFile(
    context: PlatformContext,
    file: PlatformFile
): Uri {
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file.toFile()).toCoilUri()
}

actual fun PlatformFile.deleteRecursively(): Boolean {
    return toFile().deleteRecursively()
}



actual fun PlatformFile.toUri(context: PlatformContext): Uri {
    return toFile().toUri().toCoilUri()
}

/**
 * 必须以file://开头
 */
actual fun Uri.toFile(): PlatformFile {
    return toAndroidUri().toFile().toPlatformFile()
}

actual fun PlatformFile.canRead(): Boolean {
    return toFile().canRead()
}

actual fun writeContentToUri(
    context: PlatformContext,
    uri: Uri,
    content: ByteArray
) {
    context.contentResolver.openOutputStream(uri.toAndroidUri())?.use { output ->
        output.write(content)
    }
}

actual fun readContentFromUri(context: PlatformContext, uri: Uri): ByteArray {
    return context.contentResolver.openInputStream(uri.toAndroidUri())
        ?.use { it.readBytes() }
        ?: error("Failed to read file")
}

