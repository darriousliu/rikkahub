package me.rerere.rikkahub.data.files

import io.ktor.http.ContentType
import io.ktor.http.fileExtensions
import kotlin.uuid.Uuid
import kotlin.io.encoding.Base64
import kotlinx.io.RawSource
import kotlinx.io.buffered
import kotlinx.io.files.SystemPathSeparator
import io.github.vinceglb.filekit.PlatformFile
import me.rerere.rikkahub.service.toFileUri
import me.rerere.rikkahub.utils.canonicalFile
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

// Preserve image extensions when using Ktor's MIME table (whose JPEG list starts with jfif).
internal fun commonExtensionFromMimeType(mimeType: String): String? = when (mimeType.lowercase()) {
    "image/png" -> "png"
    "image/jpeg" -> "jpg"
    "image/gif" -> "gif"
    "image/webp" -> "webp"
    "image/heic", "image/heif" -> "heic"
    "image/svg+xml" -> "svg"
    else -> runCatching { ContentType.parse(mimeType).fileExtensions().firstOrNull() }.getOrNull()
}

internal expect fun platformFileFromLocation(location: String): PlatformFile
internal expect fun fileDisplayName(file: PlatformFile): String?
internal expect fun fileNameFallback(file: PlatformFile): String?
internal expect fun fileLocation(file: PlatformFile): String
internal expect fun fileMimeType(file: PlatformFile): String?
internal expect fun openFileSource(file: PlatformFile): RawSource?
internal expect fun mimeTypeFromExtension(extension: String): String?

fun Path.toFileUri(): String = PlatformFile(toString()).toFileUri()

fun buildRelativePath(folder: String, file: Path): String = "$folder/${file.name}"

fun getRelativePathInFilesDir(filesDir: Path, file: Path): String? {
    val canonicalFile = runCatching { file.canonicalFile }.getOrNull() ?: return null
    val canonicalFilesDir = runCatching { filesDir.canonicalFile }.getOrNull() ?: return null
    val basePath = canonicalFilesDir.toString()
    val filePath = canonicalFile.toString()
    if (!filePath.startsWith("$basePath$SystemPathSeparator")) {
        return null
    }
    return filePath.removePrefix("$basePath$SystemPathSeparator").replace(SystemPathSeparator, '/')
}

fun guessMimeType(file: Path, fileName: String): String {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    if (ext.isNotEmpty()) {
        return mimeTypeFromExtension(ext)
            ?: "application/octet-stream"
    }
    return sniffMimeType(file)
}


private fun sniffMimeType(file: Path): String {
    val header = ByteArray(16)
    val read = runCatching {
        SystemFileSystem.source(file).buffered().use { input ->
            input.readAtMostTo(header)
        }
    }.getOrDefault(-1)

    if (read <= 0) return "application/octet-stream"

    if (header.startsWithBytes(0x89, 0x50, 0x4E, 0x47)) return "image/png"
    if (header.startsWithBytes(0xFF, 0xD8, 0xFF)) return "image/jpeg"
    if (header.startsWithBytes(0x47, 0x49, 0x46, 0x38)) return "image/gif"
    if (header.startsWithBytes(0x25, 0x50, 0x44, 0x46)) return "application/pdf"
    if (header.startsWithBytes(0x50, 0x4B, 0x03, 0x04)) return "application/zip"
    if (header.startsWithBytes(0x50, 0x4B, 0x05, 0x06)) return "application/zip"
    if (header.startsWithBytes(0x50, 0x4B, 0x07, 0x08)) return "application/zip"
    if (header.startsWithBytes(0x52, 0x49, 0x46, 0x46) && header.sliceArray(8..11)
            .contentEquals(byteArrayOf(0x57, 0x45, 0x42, 0x50))
    ) {
        return "image/webp"
    }
    // HEIF/HEIC/AVIF: ISO-BMFF 容器，"ftyp" box 位于字节 4..8，主品牌码位于 8..12
    if (read >= 12 && header.sliceArray(4..7).decodeToString() == "ftyp") {
        when (header.sliceArray(8..11).decodeToString()) {
            "heic", "heix", "heim", "heis",
            "hevc", "hevx", "hevm", "hevs",
            "mif1", "msf1", "heif",
                -> return "image/heic"

            "avif", "avis" -> return "image/avif"
        }
    }

    val textSample = runCatching {
        val sample = ByteArray(512)
        SystemFileSystem.source(file).buffered().use { input ->
            val len = input.readAtMostTo(sample)
            if (len <= 0) return@runCatching null
            sample.copyOf(len)
        }
    }.getOrNull()
    if (textSample != null && isLikelyText(textSample)) {
        return "text/plain"
    }

    return "application/octet-stream"
}

private fun isLikelyText(bytes: ByteArray): Boolean {
    var printable = 0
    var total = 0
    bytes.forEach { b ->
        val c = b.toInt() and 0xFF
        total += 1
        if (c == 0x09 || c == 0x0A || c == 0x0D) {
            printable += 1
        } else if (c in 0x20..0x7E) {
            printable += 1
        }
    }
    return total > 0 && printable.toDouble() / total >= 0.8
}

private fun ByteArray.startsWithBytes(vararg values: Int): Boolean {
    if (this.size < values.size) return false
    for (i in values.indices) {
        if ((this[i].toInt() and 0xFF) != values[i]) return false
    }
    return true
}
