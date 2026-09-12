package me.rerere.rikkahub.data.files

import androidx.core.net.toUri

class AndroidChatFileStore(private val filesManager: FilesManager) : ChatFileStore {
    override fun deleteChatFiles(locations: List<String>) {
        filesManager.deleteChatFiles(locations.map { it.toUri() })
    }

    override suspend fun copyChatFile(location: String): String? =
        filesManager.createChatFilesByContents(listOf(location.toUri())).firstOrNull()?.toString()
}
