package me.rerere.rikkahub.data.files

/** File operations whose Android implementation also maintains managed-file records. */
interface ChatFileStore {
    fun deleteChatFiles(locations: List<String>)
    suspend fun copyChatFile(location: String): String?
}
