package me.rerere.rikkahub.data.ai.mcp

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.delete
import io.github.vinceglb.filekit.div
import io.github.vinceglb.filekit.filesDir
import io.github.vinceglb.filekit.sink
import io.ktor.http.encodeURLPath
import kotlinx.io.buffered
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.files.FileFolders
import kotlin.uuid.Uuid

fun interface McpImageStore {
    suspend fun save(bytes: ByteArray, mimeType: String): UIMessagePart.Image
}

class FileKitMcpImageStore(
    private val uploadDirectory: PlatformFile = FileKit.filesDir / FileFolders.UPLOAD,
) : McpImageStore {
    override suspend fun save(bytes: ByteArray, mimeType: String): UIMessagePart.Image = withContext(Dispatchers.IO) {
        uploadDirectory.createDirectories()
        val destination = uploadDirectory / "mcp_${Uuid.random()}.${mimeType.toFileExtension()}"
        try {
            destination.sink().buffered().use { sink -> sink.write(bytes) }
            UIMessagePart.Image(url = "file://${destination.absolutePath().encodeURLPath()}")
        } catch (error: Throwable) {
            runCatching { destination.delete(mustExist = false) }
            throw error
        }
    }
}

private fun String.toFileExtension(): String = when (lowercase()) {
    "image/png" -> "png"
    "image/jpeg" -> "jpg"
    "image/gif" -> "gif"
    "image/webp" -> "webp"
    "image/heic", "image/heif" -> "heic"
    "image/svg+xml" -> "svg"
    else -> "bin"
}
