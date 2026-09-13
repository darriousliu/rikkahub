package me.rerere.rikkahub.data.files

import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.context
import kotlinx.io.RawSource
import kotlinx.io.asSource
import me.rerere.rikkahub.utils.toAndroidUri

internal actual fun extensionFromMimeType(mimeType: String): String? =
    MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)

internal actual fun platformFileFromLocation(location: String): PlatformFile = PlatformFile(location.toUri())

internal actual fun fileDisplayName(file: PlatformFile): String? =
    FileUtils.getFileNameFromUri(FileKit.context, file.toAndroidUri())

internal actual fun fileNameFallback(file: PlatformFile): String? = file.toAndroidUri().lastPathSegment

internal actual fun fileMimeType(file: PlatformFile): String? =
    FileUtils.getFileMimeType(FileKit.context, file.toAndroidUri())

internal actual fun openFileSource(file: PlatformFile): RawSource? =
    FileKit.context.contentResolver.openInputStream(file.toAndroidUri())?.asSource()

internal actual fun mimeTypeFromExtension(extension: String): String? =
    MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)

internal actual fun fileLocation(file: PlatformFile): String = file.toAndroidUri().toString()
