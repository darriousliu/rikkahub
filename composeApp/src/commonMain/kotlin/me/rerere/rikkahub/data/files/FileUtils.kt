package me.rerere.rikkahub.data.files

import kotlin.uuid.Uuid
import kotlin.io.encoding.Base64
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

fun createImageFileFromBase64(base64Data: String, filePath: Path): Path {
    val data = if (base64Data.startsWith("data:image")) {
        base64Data.substringAfter("base64,")
    } else {
        base64Data
    }

    val byteArray = Base64.decode(data.encodeToByteArray())
    filePath.parent?.let { SystemFileSystem.createDirectories(it) }
    SystemFileSystem.sink(filePath).buffered().use { it.write(byteArray) }
    return filePath
}

fun buildUuidFileName(displayName: String?, mimeType: String?): String {
    val extFromName = displayName
        ?.substringAfterLast('.', "")
        ?.takeIf { it.isNotBlank() && it != displayName }
        ?.lowercase()
    val extFromMime = mimeType
        ?.let { extensionFromMimeType(it.lowercase()) }
        ?.takeIf { it.isNotBlank() }
        ?.lowercase()
    val ext = extFromName ?: extFromMime ?: "bin"
    return "${Uuid.random()}.$ext"
}

internal expect fun extensionFromMimeType(mimeType: String): String?
