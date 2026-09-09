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
    fun `unfinished tools in the selected last message receive the original cancellation result`() = runTest {
        val original = interruptedToolConversation()
        val runtime = SelectionTestRuntime(original)
        val oldNode = original.messageNodes.last()
        val oldMessage = oldNode.currentMessage

        runtime.finishInterruptedPendingTools(original.id)

        assertEquals(listOf(original.id), runtime.readIds)
        assertEquals(listOf(original.id), runtime.saveAttempts.map { it.first })
        val saved = runtime.state.value
        val message = saved.messageNodes.last().currentMessage
        assertEquals(original.copy(messageNodes = saved.messageNodes), saved)
        assertSame(original.messageNodes.first(), saved.messageNodes.first())
        assertEquals(oldNode.copy(messages = listOf(oldNode.messages[0], message)), saved.messageNodes.last())
        assertEquals(oldMessage.copy(parts = message.parts, finishedAt = message.finishedAt), message)
        assertNotNull(message.finishedAt)
        assertNotEquals(oldMessage.finishedAt, message.finishedAt)
        assertEquals(oldMessage.parts.size, message.parts.size)
        oldMessage.parts.forEachIndexed { index, part ->
            val updated = message.parts[index]
            when {
                part is UIMessagePart.Tool && !part.isExecuted -> assertEquals(
                    part.copy(
                        output = listOf(UIMessagePart.Text(CANCELLED_TOOL_OUTPUT)),
                        approvalState = ToolApprovalState.Denied("Generation cancelled by user"),
                    ),
                    updated,
                )
                part is UIMessagePart.Reasoning && part.finishedAt == null -> {
                    val finishedAt = assertNotNull((updated as UIMessagePart.Reasoning).finishedAt)
                    assertEquals(part.copy(finishedAt = finishedAt), updated)
                }
                else -> assertSame(part, updated)
            }
        }
        assertEquals(original.files, saved.files)
    }

    @Test
    fun `empty or unchanged selected messages skip saving and leave other pending tools alone`() = runTest {
        val original = interruptedToolConversation()
        val completed = UIMessage.assistant("No unfinished tools").copy(parts = listOf(
            UIMessagePart.Reasoning("Keep unfinished reasoning", finishedAt = null),
            UIMessagePart.Tool("done", "tool", "{}", output = listOf(UIMessagePart.Text("done"))),
        ))
        val sources = listOf(
            original.copy(messageNodes = emptyList()),
            original.copy(messageNodes = listOf(UIMessage.user("No tools").toMessageNode())),
            original.copy(messageNodes = listOf(
                original.messageNodes.first(),
                original.messageNodes.last().copy(
                    messages = listOf(original.messageNodes.last().currentMessage, completed), selectIndex = 1,
                ),
            )),
        )
        sources.forEach { source ->
            val runtime = SelectionTestRuntime(source)
            runtime.finishInterruptedPendingTools(source.id)
            assertEquals(listOf(source.id), runtime.readIds)
            assertTrue(runtime.saveAttempts.isEmpty())
            assertSame(source, runtime.state.value)
        }
    }

    @Test
    fun `repeating completion does not save twice`() = runTest {
        val original = interruptedToolConversation()
        val runtime = SelectionTestRuntime(original)
        runtime.finishInterruptedPendingTools(original.id)
        val finished = runtime.state.value

        runtime.finishInterruptedPendingTools(original.id)

        assertEquals(1, runtime.saveAttempts.size)
        assertEquals(listOf(original.id, original.id), runtime.readIds)
        assertSame(finished, runtime.state.value)
    }

    @Test
    fun `each invocation processes the latest last node and preserves intervening updates`() = runTest {
        val original = interruptedToolConversation()
        val runtime = SelectionTestRuntime(original)
        runtime.finishInterruptedPendingTools(original.id)
        val previousNodes = runtime.state.value.messageNodes
        val newMessage = UIMessage.assistant("New pending tool").copy(
            parts = listOf(UIMessagePart.Tool("new", "tool", "{}")),
        )
        runtime.state.value = runtime.state.value.copy(
            title = "Updated title", messageNodes = previousNodes + newMessage.toMessageNode(),
        )

        runtime.finishInterruptedPendingTools(original.id)

        assertEquals(2, runtime.saveAttempts.size)
        assertEquals("Updated title", runtime.state.value.title)
        assertEquals(previousNodes, runtime.state.value.messageNodes.dropLast(1))
        val updated = runtime.state.value.messageNodes.last().currentMessage
        assertEquals(newMessage.id, updated.id)
        assertTrue((updated.parts.single() as UIMessagePart.Tool).isExecuted)
    }

    @Test
    fun `duplicate IDs within the last node retain the original replacement behavior`() = runTest {
        val original = interruptedToolConversation()
        val node = original.messageNodes.last()
        val duplicate = node.currentMessage.copy(parts = listOf(UIMessagePart.Text("Duplicate alternative")))
        val source = original.copy(messageNodes = listOf(
            original.messageNodes.first(), node.copy(messages = listOf(duplicate, node.currentMessage)),
        ))
        val runtime = SelectionTestRuntime(source)

        runtime.finishInterruptedPendingTools(source.id)

        val updatedNode = runtime.state.value.messageNodes.last()
        assertEquals(1, updatedNode.selectIndex)
        assertEquals(node.id, updatedNode.id)
        assertSame(updatedNode.messages[0], updatedNode.messages[1])
        assertNotNull(updatedNode.currentMessage.finishedAt)
    }

    @Test
    fun `invalid last nodes keep the original error instead of adding repair or skipping`() = runTest {
        val original = interruptedToolConversation()
        val node = original.messageNodes.last()
        listOf(
            node.copy(messages = emptyList(), selectIndex = 0),
            node.copy(selectIndex = -1),
            node.copy(selectIndex = node.messages.size),
        ).forEach { invalid ->
            val runtime = SelectionTestRuntime(original.copy(messageNodes = listOf(invalid)))

            val error = assertFailsWith<IllegalStateException> {
                runtime.finishInterruptedPendingTools(original.id)
            }

            assertEquals(
                "MessageNode has no valid current message: messages.size=${invalid.messages.size}, " +
                    "selectIndex=${invalid.selectIndex}",
                error.message,
            )
            assertTrue(runtime.saveAttempts.isEmpty())
        }
    }

    @Test
    fun `save failures and cancellation propagate without retries`() = runTest {
        val original = interruptedToolConversation()
        listOf(IllegalStateException("Save failed"), CancellationException("Save cancelled")).forEach { failure ->
            val runtime = SelectionTestRuntime(original) { _, _ -> throw failure }

            assertSame(failure, assertFailsWith<Throwable> { runtime.finishInterruptedPendingTools(original.id) })
            assertEquals(listOf(original.id), runtime.readIds)
            assertEquals(1, runtime.saveAttempts.size)
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
