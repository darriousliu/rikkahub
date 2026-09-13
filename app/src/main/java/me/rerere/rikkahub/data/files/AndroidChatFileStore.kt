package me.rerere.rikkahub.data.files

import androidx.core.net.toUri
import io.github.vinceglb.filekit.PlatformFile
import me.rerere.rikkahub.utils.toAndroidUri

class AndroidChatFileStore(private val filesManager: FilesManager) : ChatFileStore {
    override fun deleteChatFiles(locations: List<String>) {
        filesManager.deleteChatFiles(locations.map { it.toUri() })
    }

    override suspend fun copyChatFile(location: String): String? =
        filesManager.createChatFilesByContents(listOf(location.toUri())).firstOrNull()?.toString()

    override suspend fun createChatFilesByContents(files: List<PlatformFile>): List<String> =
        filesManager.createChatFilesByContents(files.map { it.toAndroidUri() }).map { it.toString() }

    override fun fileFromLocation(location: String): PlatformFile = PlatformFile(location.toUri())

    override fun getFileName(file: PlatformFile): String? = filesManager.getFileNameFromUri(file.toAndroidUri())

    override fun getFileMimeType(file: PlatformFile): String? = filesManager.getFileMimeType(file.toAndroidUri())
}
