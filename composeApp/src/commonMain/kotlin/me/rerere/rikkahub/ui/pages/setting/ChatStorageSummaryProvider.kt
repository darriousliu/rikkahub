package me.rerere.rikkahub.ui.pages.setting

data class ChatStorageSummary(
    val fileCount: Int,
    val totalBytes: Long,
)

fun interface ChatStorageSummaryProvider {
    suspend fun load(): ChatStorageSummary?
}

object UnavailableChatStorageSummaryProvider : ChatStorageSummaryProvider {
    override suspend fun load(): ChatStorageSummary? = null
}
