package me.rerere.rikkahub.service

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Conversation
import kotlin.time.Clock
import kotlin.uuid.Uuid

data class ChatError(
    val id: Uuid = Uuid.random(),
    val title: String? = null,
    val error: Throwable,
    val conversationId: Uuid? = null,
    val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
    val solution: ChatErrorSolution? = null,
)

enum class ChatErrorSolution {
    CheckTitleModelSettings,
}

/**
 * Platform-neutral chat state and command boundary used by shared ViewModels and pages.
 *
 * Android keeps the existing chat service implementation. Other platforms can provide the
 * same runtime without leaking Android file, resource or workspace types into common code.
 */
interface ChatRuntime {
    val errors: StateFlow<List<ChatError>>
    val generationDoneFlow: SharedFlow<Uuid>

    fun getConversationFlow(conversationId: Uuid): StateFlow<Conversation>
    fun getGenerationJobStateFlow(conversationId: Uuid): Flow<Job?>
    fun getProcessingStatusFlow(conversationId: Uuid): StateFlow<String?>
    fun getConversationJobs(): Flow<Map<Uuid, Job?>>
    fun addConversationReference(conversationId: Uuid)
    fun removeConversationReference(conversationId: Uuid)
    suspend fun initializeConversation(conversationId: Uuid)
    fun rememberConversation(conversationId: Uuid)
    fun shouldCreateNewConversationOnAssistantSwitch(): Boolean
    fun deleteChatFiles(urls: List<String>)

    fun addError(
        error: Throwable,
        conversationId: Uuid? = null,
        title: String? = null,
        solution: ChatErrorSolution? = null,
    )

    fun dismissError(id: Uuid)
    fun clearAllErrors()
    fun sendMessage(conversationId: Uuid, content: List<UIMessagePart>, answer: Boolean = true)
    suspend fun editMessage(conversationId: Uuid, messageId: Uuid, parts: List<UIMessagePart>)
    suspend fun compressConversation(
        conversationId: Uuid,
        conversation: Conversation,
        additionalPrompt: String,
        targetTokens: Int,
        keepRecentMessages: Int = 32,
    ): Result<Unit>

    suspend fun forkConversationAtMessage(conversationId: Uuid, messageId: Uuid): Conversation
    suspend fun deleteMessage(conversationId: Uuid, message: UIMessage)
    fun regenerateAtMessage(
        conversationId: Uuid,
        message: UIMessage,
        regenerateAssistantMsg: Boolean = true,
    )

    fun handleToolApproval(
        conversationId: Uuid,
        toolCallId: String,
        approved: Boolean,
        reason: String = "",
        answer: String? = null,
    )

    suspend fun stopGeneration(conversationId: Uuid)
    suspend fun saveConversation(conversationId: Uuid, conversation: Conversation)
    fun updateConversationState(conversationId: Uuid, update: (Conversation) -> Conversation)
    fun translateMessage(conversationId: Uuid, message: UIMessage, targetLanguageTag: String)
    suspend fun generateTitle(conversationId: Uuid, conversation: Conversation, force: Boolean = false)
    suspend fun generateSuggestion(conversationId: Uuid, conversation: Conversation)
    fun clearTranslationField(conversationId: Uuid, messageId: Uuid)
    fun hasGeneratingConversationInFolder(folderId: Uuid): Boolean
    suspend fun deleteFolder(folderId: Uuid)
    suspend fun moveConversationToFolder(conversationId: Uuid, folderId: Uuid?)
}
