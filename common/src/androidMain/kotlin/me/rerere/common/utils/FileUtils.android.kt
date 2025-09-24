@file:Suppress("NOTHING_TO_INLINE")

package me.rerere.common.utils

import androidx.core.content.FileProvider
import androidx.core.net.toFile
import coil3.Uri
import coil3.toCoilUri
import io.github.vinceglb.filekit.AndroidFile
import io.github.vinceglb.filekit.PlatformFile
import me.rerere.common.PlatformContext
import java.io.File

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
