package me.rerere.rikkahub.data.files

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.mimeType
import io.github.vinceglb.filekit.name
import me.rerere.rikkahub.service.toLocalFilePath

/** File operations whose Android implementation also maintains managed-file records. */
interface ChatFileStore {
    fun deleteChatFiles(locations: List<String>)
    suspend fun copyChatFile(location: String): String?
    suspend fun createChatFilesByContents(files: List<PlatformFile>): List<String>
    fun fileFromLocation(location: String): PlatformFile = PlatformFile(location.toLocalFilePath())
    fun getFileName(file: PlatformFile): String? = file.name
    fun getFileMimeType(file: PlatformFile): String? = file.mimeType()?.toString()
}
