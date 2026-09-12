package me.rerere.rikkahub.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.files.ChatFileStore

internal class FileKitChatFileStore(
    private val scope: CoroutineScope,
    private val attachmentStore: SharedChatAttachmentStore,
) : ChatFileStore {
    override fun deleteChatFiles(locations: List<String>) {
        scope.launch { attachmentStore.delete(locations) }
    }

    override suspend fun copyChatFile(location: String): String? = attachmentStore.copyIntoSandbox(location)
}
