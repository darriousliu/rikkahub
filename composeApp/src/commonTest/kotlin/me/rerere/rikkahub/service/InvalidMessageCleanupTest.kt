package me.rerere.rikkahub.service

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
import kotlin.test.assertSame
import kotlin.time.Instant
import kotlin.uuid.Uuid

class InvalidMessageCleanupTest {
    @Test
    fun `approval state and output presence retain the original keep or remove rules`() {
        val states = listOf(
            ToolApprovalState.Auto to false,
            ToolApprovalState.Pending to false,
            ToolApprovalState.Approved to true,
            ToolApprovalState.Denied("Denied reason") to true,
            ToolApprovalState.Answered("Answer") to true,
        )
        states.forEach { (state, resumable) ->
            listOf(emptyList(), listOf(UIMessagePart.Text(""))).forEach { output ->
                val node = UIMessage.assistant("Tool message").copy(
                    parts = listOf(cleanupTool(state).copy(output = output)),
                ).toMessageNode()
                val source = Conversation.ofId(Uuid.random(), messages = listOf(node))
                val expectedNodes = if (resumable || output.isNotEmpty()) listOf(node) else emptyList()

                val updated = buildConversationAfterInvalidMessageCleanup(source)

                assertEquals(source.copy(messageNodes = expectedNodes), updated)
                if (expectedNodes.isNotEmpty()) assertSame(node, updated.messageNodes.single())
            }
        }
    }

    @Test
    fun `only an unexecuted resumable tool keeps a message with other unresolved tools`() {
        val resumableStates = listOf(
            ToolApprovalState.Approved, ToolApprovalState.Denied("Reason"), ToolApprovalState.Answered("Answer"),
        )
        resumableStates.forEach { state ->
            val parts = listOf(cleanupTool(ToolApprovalState.Auto), cleanupTool(ToolApprovalState.Pending))
            val resumable = cleanupTool(state)
            val node = UIMessage.assistant("Mixed tools").copy(parts = parts + resumable).toMessageNode()
            val source = Conversation.ofId(Uuid.random(), messages = listOf(node))
            assertSame(node, buildConversationAfterInvalidMessageCleanup(source).messageNodes.single())

            val executed = resumable.copy(output = listOf(UIMessagePart.Text("Done")))
            val withExecuted = source.copy(messageNodes = listOf(
                node.copy(messages = listOf(node.currentMessage.copy(parts = parts + executed))),
            ))
            assertEquals(
                withExecuted.copy(messageNodes = emptyList()),
                buildConversationAfterInvalidMessageCleanup(withExecuted),
            )
        }
    }

    @Test
    fun `removing each selected branch keeps numeric selection and processes only one branch per call`() {
        val messages = listOf("First", "Second", "Third").map { cleanupMessage(it) }
        val remaining = listOf(
            listOf(messages[1], messages[2]),
            listOf(messages[0], messages[2]),
            listOf(messages[0], messages[1]),
        )
        val selections = listOf(0, 0, 1)
        remaining.forEachIndexed { selected, expectedMessages ->
            val node = MessageNode(messages = messages, selectIndex = selected, isFavorite = true)
            val source = Conversation.ofId(Uuid.random(), messages = listOf(node))

            val updated = buildConversationAfterInvalidMessageCleanup(source)

            assertEquals(
                source.copy(messageNodes = listOf(
                    node.copy(messages = expectedMessages, selectIndex = selections[selected]),
                )),
                updated,
            )
            assertEquals(expectedMessages[selections[selected]], updated.currentMessages.single())
            assertEquals(messages, source.messageNodes.single().messages)
            val next = buildConversationAfterInvalidMessageCleanup(updated)
            assertEquals(1, next.messageNodes.single().messages.size)
            assertEquals(0, next.messageNodes.single().selectIndex)
            assertEquals(
                source.copy(messageNodes = emptyList()),
                buildConversationAfterInvalidMessageCleanup(next),
            )
        }
    }

    @Test
    fun `cleanup preserves retained nodes metadata and file references while removing empty result nodes`() {
        val original = invalidMessageCleanupConversation()
        val target = original.messageNodes[1].copy(isFavorite = true)
        val source = original.copy(
            messageNodes = listOf(original.messageNodes[0], target) + original.messageNodes.drop(2),
            newConversation = true,
        )

        val updated = buildConversationAfterInvalidMessageCleanup(source)

        assertEquals(
            source.copy(messageNodes = listOf(
                source.messageNodes[0],
                target.copy(messages = listOf(target.messages[0], target.messages[2]), selectIndex = 0),
                source.messageNodes[3],
            )),
            updated,
        )
        assertSame(source.messageNodes[0], updated.messageNodes[0])
        assertSame(source.messageNodes[3], updated.messageNodes[2])
        assertSame(target.messages[2], updated.messageNodes[1].messages[1])
        assertEquals(listOf("file:///shared-cleanup.png", "file:///shared-cleanup.png"), updated.files)
        assertEquals(4, source.messageNodes.size)
        assertEquals(3, source.messageNodes[1].messages.size)
    }

    @Test
    fun `duplicate selected IDs are all removed only within nodes whose selected message is invalid`() {
        val invalid = cleanupMessage("Invalid duplicate")
        val duplicate = invalid.copy(parts = listOf(UIMessagePart.Text("Valid duplicate")))
        val survivor = UIMessage.assistant("Survivor")
        val target = MessageNode(messages = listOf(duplicate, survivor, invalid), selectIndex = 2)
        val later = MessageNode(messages = listOf(duplicate, cleanupMessage("Unselected invalid")))
        val source = Conversation.ofId(Uuid.random(), messages = listOf(target, later))

        val updated = buildConversationAfterInvalidMessageCleanup(source)

        assertEquals(
            source.copy(messageNodes = listOf(target.copy(messages = listOf(survivor), selectIndex = 0), later)),
            updated,
        )
        assertSame(later, updated.messageNodes[1])
    }

    @Test
    fun `initially empty nodes and invalid selected indices preserve the original failure before repair`() {
        val good = UIMessage.user("Keep this message").toMessageNode()
        val invalidNodes = listOf(
            MessageNode(messages = emptyList()),
            good.copy(selectIndex = -1),
            good.copy(selectIndex = 1),
            good.copy(selectIndex = Int.MAX_VALUE),
        )
        invalidNodes.forEach { invalid ->
            val source = Conversation.ofId(Uuid.random(), messages = listOf(good, invalid))

            val failure = assertFailsWith<IllegalStateException> {
                buildConversationAfterInvalidMessageCleanup(source)
            }

            assertEquals(
                "MessageNode has no valid current message: messages.size=${invalid.messages.size}, " +
                    "selectIndex=${invalid.selectIndex}",
                failure.message,
            )
            assertEquals(listOf(good, invalid), source.messageNodes)
        }
    }

    @Test
    fun `empty conversations and valid selected messages keep nested tools and unselected branches intact`() {
        val pending = cleanupTool(ToolApprovalState.Pending)
        val executed = pending.copy(output = listOf(pending))
        val nodes = listOf(
            UIMessage.assistant("").copy(parts = emptyList()).toMessageNode(),
            UIMessage.user("No tools").toMessageNode(),
            UIMessage.assistant("Nested pending output").copy(parts = listOf(executed)).toMessageNode(),
            MessageNode(
                messages = listOf(cleanupMessage("Unselected"), UIMessage.assistant("Selected")), selectIndex = 1,
            ),
        )
        listOf(emptyList(), nodes).forEach { sourceNodes ->
            val source = Conversation.ofId(Uuid.random(), messages = sourceNodes)
            val updated = buildConversationAfterInvalidMessageCleanup(source)
            assertEquals(source, updated)
            sourceNodes.forEachIndexed { index, node -> assertSame(node, updated.messageNodes[index]) }
        }
    }
}

private fun cleanupTool(state: ToolApprovalState) = UIMessagePart.Tool(
    toolCallId = "cleanup-tool",
    toolName = "tool",
    input = "unparsed input",
    approvalState = state,
    metadata = JsonObject(mapOf("keep" to JsonPrimitive("metadata"))),
)

private fun cleanupMessage(text: String, state: ToolApprovalState = ToolApprovalState.Pending): UIMessage =
    UIMessage.assistant(text).let { it.copy(parts = it.parts + cleanupTool(state)) }

internal fun invalidMessageCleanupConversation(): Conversation {
    val shared = UIMessagePart.Image("file:///shared-cleanup.png")
    val selected = cleanupMessage("invalidbranchmarker").let {
        it.copy(parts = it.parts + UIMessagePart.Image("file:///invalid-branch.png"))
    }
    val removed = cleanupMessage("invalidnodemarker", ToolApprovalState.Auto).let {
        it.copy(parts = it.parts + UIMessagePart.Image("file:///invalid-node.png"))
    }
    val resumable = cleanupMessage("resumablemarker", ToolApprovalState.Answered("Keep answer")).let {
        it.copy(parts = it.parts + cleanupTool(ToolApprovalState.Pending))
    }
    return Conversation.ofId(
        Uuid.random(),
        messages = listOf(
            UIMessage.user("questionmarker").let { it.copy(parts = it.parts + shared) }.toMessageNode(),
            MessageNode(
                messages = listOf(
                    UIMessage.assistant("keepbeforemarker").let { it.copy(parts = it.parts + shared) },
                    selected,
                    UIMessage.assistant("keepaftermarker").copy(
                        translation = "Keep translation", modelId = Uuid.random(),
                    ),
                ),
                selectIndex = 1,
            ),
            removed.toMessageNode(),
            resumable.toMessageNode(),
        ),
    ).copy(
        title = "Invalid message cleanup",
        chatSuggestions = listOf("Keep suggestion"),
        isPinned = true,
        createAt = Instant.fromEpochMilliseconds(1_000),
        updateAt = Instant.fromEpochMilliseconds(2_000),
        customSystemPrompt = "Keep prompt",
        modeInjectionIds = setOf(Uuid.random()),
        lorebookIds = setOf(Uuid.random()),
        workspaceCwd = "/workspace",
        folderId = Uuid.random(),
    )
}
