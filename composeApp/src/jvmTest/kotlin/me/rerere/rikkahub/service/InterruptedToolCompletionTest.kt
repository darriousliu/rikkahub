package me.rerere.rikkahub.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import me.rerere.ai.ui.ToolApprovalState
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.model.toMessageNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

private const val CANCELLED_TOOL_OUTPUT =
    """{"status":"cancelled","error":"Generation cancelled by user before tool execution completed."}"""

class InterruptedToolCompletionTest {
    @Test
    fun `completion retains metadata and is idempotent through the actual service`() = runTest {
        ChatServiceTestFixture().use { f ->
            val original = interruptedToolConversation()
            f.load(original)
            f.service.finishInterruptedPendingTools(original.id)
            val saved = f.service.getConversationFlow(original.id).value
            val message = saved.messageNodes.last().currentMessage
            assertEquals(original.copy(messageNodes = saved.messageNodes), saved)
            assertEquals(original.messageNodes.first(), saved.messageNodes.first())
            assertEquals(original.messageNodes.last().messages[0], saved.messageNodes.last().messages[0])
            assertNotNull(message.finishedAt)
            val tools = message.parts.filterIsInstance<UIMessagePart.Tool>()
            assertTrue(tools.all { it.isExecuted })
            assertTrue(tools.take(5).all {
                it.output == listOf(UIMessagePart.Text(CANCELLED_TOOL_OUTPUT)) &&
                    it.approvalState == ToolApprovalState.Denied("Generation cancelled by user")
            })
            assertEquals(original.messageNodes.last().currentMessage.parts.last(), message.parts.last())
            assertEquals(original.files, saved.files)
            assertEquals(saved, f.repository.getConversationById(original.id))
            f.service.finishInterruptedPendingTools(original.id)
            assertSame(saved, f.service.getConversationFlow(original.id).value)
        }
    }

    @Test
    fun `each completion uses the latest last node and preserves intervening changes`() = runTest {
        ChatServiceTestFixture().use { f ->
            val original = interruptedToolConversation()
            f.load(original)
            f.service.finishInterruptedPendingTools(original.id)
            val previous = f.service.getConversationFlow(original.id).value.messageNodes
            val next = UIMessage.assistant("New tool").copy(parts = listOf(UIMessagePart.Tool("new", "tool", "{}")))
            f.service.updateConversationState(original.id) {
                it.copy(title = "Latest", messageNodes = previous + next.toMessageNode())
            }
            f.service.finishInterruptedPendingTools(original.id)
            val saved = f.repository.getConversationById(original.id)!!
            assertEquals("Latest", saved.title)
            assertEquals(previous, saved.messageNodes.dropLast(1))
            assertTrue(saved.messageNodes.last().currentMessage.getTools().single().isExecuted)
        }
    }

    @Test
    fun `unchanged messages do not reach SQLite`() = runTest {
        ChatServiceTestFixture().use { f ->
            val source = Conversation.ofId(Uuid.random(), messages = listOf(UIMessage.user("No tools").toMessageNode()))
            f.service.updateConversationState(source.id) { source }
            f.service.finishInterruptedPendingTools(source.id)
            assertSame(source, f.service.getConversationFlow(source.id).value)
            assertEquals(null, f.repository.getConversationById(source.id))
        }
    }
}

internal fun interruptedToolConversation(): Conversation {
    val metadata = JsonObject(mapOf("keep" to JsonPrimitive("metadata")))
    val pendingTools = listOf(
        ToolApprovalState.Auto, ToolApprovalState.Pending, ToolApprovalState.Approved,
        ToolApprovalState.Denied("Existing reason"), ToolApprovalState.Answered("Existing answer"),
    ).mapIndexed { index, state ->
        UIMessagePart.Tool("pending-$index", "tool", "input-$index", approvalState = state, metadata = metadata)
    }
    val earlier = UIMessage.assistant("earliermarker").let { it.copy(parts = it.parts + pendingTools[0]) }
    val alternative = UIMessage.assistant("alternativemarker").let { it.copy(parts = it.parts + pendingTools[1]) }
    val current = UIMessage.assistant("currentmarker").copy(
        parts = listOf(
            UIMessagePart.Text("currentmarker", metadata),
            UIMessagePart.Reasoning("Unfinished reasoning", finishedAt = null, metadata = metadata),
            UIMessagePart.Reasoning("Finished reasoning", finishedAt = Instant.fromEpochMilliseconds(2_000)),
            UIMessagePart.Image("file:///retained.png", metadata),
        ) + pendingTools + UIMessagePart.Tool(
            "done", "tool", "{}", output = listOf(UIMessagePart.Text("Done"), pendingTools[2]),
            approvalState = ToolApprovalState.Pending, metadata = metadata,
        ),
        finishedAt = LocalDateTime(2020, 1, 1, 0, 0),
        modelId = Uuid.random(),
        translation = "Keep translation",
    )
    return Conversation.ofId(
        Uuid.random(),
        messages = listOf(
            earlier.toMessageNode(),
            MessageNode(messages = listOf(alternative, current), selectIndex = 1),
        ),
    ).copy(
        title = "Interrupted tools",
        chatSuggestions = listOf("Keep suggestion"),
        isPinned = true,
        createAt = Instant.fromEpochMilliseconds(1_000),
        updateAt = Instant.fromEpochMilliseconds(2_000),
        customSystemPrompt = "Keep prompt",
        modeInjectionIds = setOf(Uuid.random()),
        lorebookIds = setOf(Uuid.random()),
        folderId = Uuid.random(),
        workspaceCwd = "/workspace",
    )
}
