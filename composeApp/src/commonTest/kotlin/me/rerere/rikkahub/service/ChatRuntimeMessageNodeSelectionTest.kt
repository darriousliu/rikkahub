package me.rerere.rikkahub.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.model.toMessageNode
import me.rerere.rikkahub.web.BadRequestException
import me.rerere.rikkahub.web.NotFoundException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class ChatRuntimeMessageNodeSelectionTest {
    @Test
    fun `selecting an alternative changes only the target index and saves once`() = runTest {
        val original = selectionConversation()
        val target = original.messageNodes[1]
        val runtime = SelectionTestRuntime(original)

        runtime.selectMessageNode(original.id, target.id, 1)

        assertEquals(listOf(original.id), runtime.readIds)
        assertEquals(listOf(original.id), runtime.saveAttempts.map { it.first })
        val saved = runtime.saveAttempts.single().second
        assertEquals(
            original.copy(messageNodes = listOf(
                original.messageNodes[0], target.copy(selectIndex = 1), original.messageNodes[2],
            )),
            saved,
        )
        assertEquals(target.messages[1], saved.currentMessages[1])
        assertEquals(target.messages, saved.messageNodes[1].messages)
        assertEquals(0, original.messageNodes[1].selectIndex)
    }

    @Test
    fun `selecting the current alternative does not save`() = runTest {
        val original = selectionConversation()
        val runtime = SelectionTestRuntime(original)

        runtime.selectMessageNode(original.id, original.messageNodes[1].id, 0)

        assertTrue(runtime.saveAttempts.isEmpty())
        assertEquals(original, runtime.state.value)
    }

    @Test
    fun `missing node reports the original 404 before validating the index`() = runTest {
        val original = selectionConversation()
        val runtime = SelectionTestRuntime(original)

        val error = assertFailsWith<NotFoundException> {
            runtime.selectMessageNode(original.id, Uuid.random(), -1)
        }

        assertEquals(404, error.statusCode)
        assertEquals("Message node not found", error.message)
        assertTrue(runtime.saveAttempts.isEmpty())
        assertEquals(original, runtime.state.value)
    }

    @Test
    fun `invalid indices report the original 400 without saving`() = runTest {
        val original = selectionConversation()
        val target = original.messageNodes[1]
        val runtime = SelectionTestRuntime(original)

        listOf(-1, target.messages.size, Int.MAX_VALUE).forEach { index ->
            val error = assertFailsWith<BadRequestException> {
                runtime.selectMessageNode(original.id, target.id, index)
            }
            assertEquals(400, error.statusCode)
            assertEquals("Invalid selectIndex", error.message)
        }
        assertTrue(runtime.saveAttempts.isEmpty())
        assertEquals(original, runtime.state.value)
    }

    @Test
    fun `an empty node is rejected even when its stored index matches`() = runTest {
        val node = MessageNode(messages = emptyList(), selectIndex = 0)
        val original = selectionConversation().copy(messageNodes = listOf(node))
        val runtime = SelectionTestRuntime(original)

        assertFailsWith<BadRequestException> {
            runtime.selectMessageNode(original.id, node.id, 0)
        }
        assertTrue(runtime.saveAttempts.isEmpty())
    }

    @Test
    fun `every invocation reads the current runtime conversation`() = runTest {
        val original = selectionConversation()
        val target = original.messageNodes[1]
        val runtime = SelectionTestRuntime(original)
        runtime.selectMessageNode(original.id, target.id, 1)
        val appended = UIMessage.user("New message").toMessageNode()
        runtime.state.value = runtime.state.value.copy(
            title = "Updated title",
            messageNodes = runtime.state.value.messageNodes + appended,
        )

        runtime.selectMessageNode(original.id, target.id, 0)

        assertEquals(listOf(original.id, original.id), runtime.readIds)
        assertEquals("Updated title", runtime.saveAttempts.last().second.title)
        assertEquals(appended, runtime.saveAttempts.last().second.messageNodes.last())
        assertEquals(target.messages[0], runtime.state.value.currentMessages[1])
    }

    @Test
    fun `storage errors and cancellation propagate without retries`() = runTest {
        val original = selectionConversation()
        val target = original.messageNodes[1]
        val failed = SelectionTestRuntime(original) { _, _ -> error("Storage failed") }
        val cancelled = SelectionTestRuntime(original) { _, _ -> throw CancellationException("Cancelled") }

        assertEquals("Storage failed", assertFailsWith<IllegalStateException> {
            failed.selectMessageNode(original.id, target.id, 1)
        }.message)
        assertEquals("Cancelled", assertFailsWith<CancellationException> {
            cancelled.selectMessageNode(original.id, target.id, 1)
        }.message)
        assertEquals(1, failed.saveAttempts.size)
        assertEquals(1, cancelled.saveAttempts.size)
    }
}

internal fun selectionConversation(): Conversation = Conversation.ofId(
    Uuid.random(),
    messages = listOf(
        UIMessage.user("Question").toMessageNode(),
        MessageNode(messages = listOf(
            UIMessage.assistant("originalmarker").copy(translation = "Original translation"),
            UIMessage.assistant("alternativemarker").copy(translation = "Alternative translation"),
        )),
        UIMessage.user("Follow-up").toMessageNode(),
    ),
).copy(
    title = "Branch selection",
    chatSuggestions = listOf("Keep this suggestion"),
    isPinned = true,
    createAt = Instant.fromEpochMilliseconds(1_000),
    updateAt = Instant.fromEpochMilliseconds(2_000),
    customSystemPrompt = "Keep this prompt",
    modeInjectionIds = setOf(Uuid.random()),
    lorebookIds = setOf(Uuid.random()),
    workspaceCwd = "/workspace",
    folderId = Uuid.random(),
)

/** Exercises the inherited command; unexpected calls fail instead of hiding new side effects. */
internal class SelectionTestRuntime(
    initialConversation: Conversation,
    private val onSave: suspend (Uuid, Conversation) -> Unit = { _, _ -> },
) : ChatRuntime {
    val state = MutableStateFlow(initialConversation)
    val readIds = mutableListOf<Uuid>()
    val saveAttempts = mutableListOf<Pair<Uuid, Conversation>>()

    override fun getConversationFlow(conversationId: Uuid): StateFlow<Conversation> {
        readIds += conversationId
        return state
    }

    override suspend fun saveConversation(conversationId: Uuid, conversation: Conversation) {
        saveAttempts += conversationId to conversation
        onSave(conversationId, conversation)
        state.value = conversation
    }

    override val errors: StateFlow<List<ChatError>> get() = unused()
    override val generationDoneFlow: SharedFlow<Uuid> get() = unused()
    override fun getGenerationJobStateFlow(conversationId: Uuid): Flow<Job?> = unused()
    override fun getProcessingStatusFlow(conversationId: Uuid): StateFlow<String?> = unused()
    override fun getConversationJobs(): Flow<Map<Uuid, Job?>> = unused()
    override fun addConversationReference(conversationId: Uuid) = unused()
    override fun removeConversationReference(conversationId: Uuid) = unused()
    override suspend fun initializeConversation(conversationId: Uuid) = unused()
    override fun rememberConversation(conversationId: Uuid) = unused()
    override fun shouldCreateNewConversationOnAssistantSwitch(): Boolean = unused()
    override fun deleteChatFiles(urls: List<String>) = unused()
    override fun addError(
        error: Throwable, conversationId: Uuid?, title: String?, solution: ChatErrorSolution?,
    ) = unused()
    override fun dismissError(id: Uuid) = unused()
    override fun clearAllErrors() = unused()
    override fun sendMessage(conversationId: Uuid, content: List<UIMessagePart>, answer: Boolean) = unused()
    override suspend fun editMessage(conversationId: Uuid, messageId: Uuid, parts: List<UIMessagePart>) = unused()
    override suspend fun compressConversation(
        conversationId: Uuid, conversation: Conversation, additionalPrompt: String,
        targetTokens: Int, keepRecentMessages: Int,
    ): Result<Unit> = unused()
    override suspend fun forkConversationAtMessage(conversationId: Uuid, messageId: Uuid): Conversation = unused()
    override suspend fun deleteMessage(conversationId: Uuid, message: UIMessage) = unused()
    override fun regenerateAtMessage(
        conversationId: Uuid, message: UIMessage, regenerateAssistantMsg: Boolean,
    ) = unused()
    override fun handleToolApproval(
        conversationId: Uuid, toolCallId: String, approved: Boolean, reason: String, answer: String?,
    ) = unused()
    override suspend fun stopGeneration(conversationId: Uuid) = unused()
    override fun updateConversationState(conversationId: Uuid, update: (Conversation) -> Conversation) = unused()
    override fun translateMessage(conversationId: Uuid, message: UIMessage, targetLanguageTag: String) = unused()
    override suspend fun generateTitle(conversationId: Uuid, conversation: Conversation, force: Boolean) = unused()
    override suspend fun generateSuggestion(conversationId: Uuid, conversation: Conversation) = unused()
    override fun clearTranslationField(conversationId: Uuid, messageId: Uuid) = unused()
    override fun hasGeneratingConversationInFolder(folderId: Uuid): Boolean = unused()
    override suspend fun deleteFolder(folderId: Uuid) = unused()
    override suspend fun moveConversationToFolder(conversationId: Uuid, folderId: Uuid?) = unused()

    private fun unused(): Nothing = error("Unrelated runtime operation")
}
