package me.rerere.rikkahub.data.files

import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.mimeType
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.source
import io.ktor.http.fromFileExtension
import kotlinx.io.RawSource
import me.rerere.rikkahub.service.toLocalFilePath
import io.ktor.http.ContentType
import io.ktor.http.fileExtensions

internal actual fun extensionFromMimeType(mimeType: String): String? =
    runCatching { ContentType.parse(mimeType).fileExtensions().firstOrNull() }.getOrNull()

internal actual fun platformFileFromLocation(location: String): PlatformFile = PlatformFile(location.toLocalFilePath())
internal actual fun fileDisplayName(file: PlatformFile): String? = file.name
internal actual fun fileNameFallback(file: PlatformFile): String? = file.name
internal actual fun fileMimeType(file: PlatformFile): String? = file.mimeType()?.toString()
internal actual fun openFileSource(file: PlatformFile): RawSource? = file.source()
internal actual fun mimeTypeFromExtension(extension: String): String? =
    ContentType.fromFileExtension(extension).firstOrNull()?.toString()

internal actual fun fileLocation(file: PlatformFile): String = file.absolutePath()
