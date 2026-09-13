package me.rerere.rikkahub.data.repository

import androidx.core.net.toUri
import me.rerere.rikkahub.data.files.FilesManager

class AndroidConversationFileStore(
    private val filesManager: FilesManager,
) : ConversationFileStore {
    override suspend fun deleteChatFiles(urls: List<String>) {
        filesManager.deleteChatFiles(urls.map { it.toUri() })
    }
}
