package me.rerere.rikkahub.service

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.delete
import io.github.vinceglb.filekit.extension
import io.github.vinceglb.filekit.mimeType
import io.ktor.http.decodeURLPart
import io.ktor.http.encodeURLPath
import kotlinx.coroutines.CancellationException
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.platform.FileKitPlatformFileStore
import me.rerere.rikkahub.platform.FileStoreArea
import me.rerere.rikkahub.platform.PlatformFileStore

internal class SharedChatAttachmentStore(
    private val fileStore: PlatformFileStore = FileKitPlatformFileStore(),
) {
    suspend fun import(files: List<PlatformFile>): List<UIMessagePart> = buildList {
        files.forEach { source ->
            val stored = fileStore.copyIntoSandbox(source, FileStoreArea.ATTACHMENTS).getOrNull()
                ?: return@forEach
            val uri = stored.file.toFileUri()
            val mime = source.mimeType()?.toString() ?: mimeTypeForExtension(source.extension)
            add(
                when {
                    mime.startsWith("image/") -> UIMessagePart.Image(uri)
                    mime.startsWith("video/") -> UIMessagePart.Video(uri)
                    mime.startsWith("audio/") -> UIMessagePart.Audio(uri)
                    else -> UIMessagePart.Document(
                        url = uri,
                        fileName = stored.originalName,
                        mime = mime,
                    )
                },
            )
        }
    }

    suspend fun importLocations(locations: List<String>): List<UIMessagePart> = import(
        locations.mapNotNull { location ->
            runCatching { PlatformFile(location.toLocalFilePath()) }.getOrNull()
        },
    )

    suspend fun delete(locations: List<String>) {
        locations.forEach { location ->
            try {
                PlatformFile(location.toLocalFilePath()).delete(mustExist = false)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
            }
        }
    }
}

private fun PlatformFile.toFileUri(): String = "file://${absolutePath().encodeURLPath()}"

private fun String.toLocalFilePath(): String = removePrefix("file://").decodeURLPart()

private fun mimeTypeForExtension(extension: String): String = when (extension.lowercase()) {
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "heic", "heif" -> "image/heic"
    "mp4" -> "video/mp4"
    "mov" -> "video/quicktime"
    "webm" -> "video/webm"
    "mp3" -> "audio/mpeg"
    "m4a" -> "audio/mp4"
    "wav" -> "audio/wav"
    "ogg" -> "audio/ogg"
    "pdf" -> "application/pdf"
    "json" -> "application/json"
    "md", "txt", "csv", "log" -> "text/plain"
    else -> "application/octet-stream"
}
