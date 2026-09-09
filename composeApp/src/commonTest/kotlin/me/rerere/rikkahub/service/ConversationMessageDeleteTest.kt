package me.rerere.rikkahub.service

import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.model.toMessageNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.time.Instant
import kotlin.uuid.Uuid

class ConversationMessageDeleteTest {
    @Test
    fun `deleting each branch preserves the original numeric selection rule and other data`() {
        val original = deletionConversation()
        val target = original.messageNodes[1]
        val remainingByDeletedIndex = listOf(
            listOf(target.messages[1], target.messages[2]),
            listOf(target.messages[0], target.messages[2]),
            listOf(target.messages[0], target.messages[1]),
        )
        val expectedIndices = listOf(0, 1, 1)

        remainingByDeletedIndex.forEachIndexed { deletedIndex, remaining ->
            expectedIndices.forEachIndexed { selectedIndex, expectedIndex ->
                val selected = target.copy(selectIndex = selectedIndex, isFavorite = true)
                val source = original.copy(
                    messageNodes = listOf(original.messageNodes[0], selected, original.messageNodes[2]),
                    newConversation = true,
                )
                val updated = assertNotNull(
                    buildConversationAfterMessageDelete(source, target.messages[deletedIndex].id),
                )

                assertEquals(
                    source.copy(messageNodes = listOf(
                        source.messageNodes[0],
                        selected.copy(messages = remaining, selectIndex = expectedIndex),
                        source.messageNodes[2],
                    )),
                    updated,
                )
                assertEquals(remaining[expectedIndex], updated.currentMessages[1])
                assertSame(source.messageNodes[0], updated.messageNodes[0])
                assertSame(source.messageNodes[2], updated.messageNodes[2])
                assertEquals(target.messages, source.messageNodes[1].messages)
            }
        }
    }

    @Test
    fun `deleting the last message removes only its node and can leave an empty conversation`() {
        val original = deletionConversation()
        val target = original.messageNodes.last()
        val unrelatedEmptyNode = MessageNode(messages = emptyList())
        val source = original.copy(messageNodes = original.messageNodes + unrelatedEmptyNode)

        assertEquals(
            source.copy(messageNodes = listOf(source.messageNodes[0], source.messageNodes[1], unrelatedEmptyNode)),
            buildConversationAfterMessageDelete(source, target.currentMessage.id),
        )
        val single = original.copy(messageNodes = listOf(target))
        assertEquals(
            single.copy(messageNodes = emptyList()),
            buildConversationAfterMessageDelete(single, target.currentMessage.id),
        )
    }

    @Test
    fun `a missing message returns null including empty conversations and nodes`() {
        val original = deletionConversation()
        val emptyNode = MessageNode(messages = emptyList())

        assertNull(buildConversationAfterMessageDelete(original, Uuid.random()))
        assertNull(buildConversationAfterMessageDelete(original.copy(messageNodes = emptyList()), Uuid.random()))
        assertNull(buildConversationAfterMessageDelete(original.copy(messageNodes = listOf(emptyNode)), Uuid.random()))
    }

    @Test
    fun `duplicate IDs are all removed within the first matching node while later nodes stay intact`() {
        val original = deletionConversation()
        val target = original.messageNodes[1]
        val repeated = target.messages[0]
        val first = target.copy(messages = listOf(repeated, target.messages[1], repeated), selectIndex = 2)
        val later = repeated.toMessageNode()
        val source = original.copy(messageNodes = listOf(original.messageNodes[0], first, later))

        val updated = assertNotNull(buildConversationAfterMessageDelete(source, repeated.id))

        assertEquals(
            source.copy(messageNodes = listOf(
                source.messageNodes[0], first.copy(messages = listOf(target.messages[1]), selectIndex = 0), later,
            )),
            updated,
        )
        assertSame(later, updated.messageNodes[2])
    }

    @Test
    fun `invalid stored indices retain the original upper bound only adjustment`() {
        val original = deletionConversation()
        val target = original.messageNodes[1]
        listOf(Int.MIN_VALUE to Int.MIN_VALUE, -1 to -1, 3 to 1, Int.MAX_VALUE to 1).forEach { (stored, expected) ->
            val source = original.copy(messageNodes = listOf(target.copy(selectIndex = stored)))
            val updated = assertNotNull(buildConversationAfterMessageDelete(source, target.messages[0].id))

            assertEquals(expected, updated.messageNodes.single().selectIndex)
            assertEquals(target.messages.drop(1), updated.messageNodes.single().messages)
        }
    }
}

internal fun deletionConversation(): Conversation {
    val sharedImage = UIMessagePart.Image("file:///shared-image.png")
    return Conversation.ofId(
        Uuid.random(),
        messages = listOf(
            UIMessage.user("questionmarker").let { it.copy(parts = it.parts + sharedImage) }.toMessageNode(),
            MessageNode(
                messages = listOf(
                    UIMessage.assistant("firstdeletemarker").let {
                        it.copy(parts = it.parts + UIMessagePart.Image("file:///deleted-branch.png"))
                    },
                    UIMessage.assistant("keepsecondmarker").copy(translation = "Second translation"),
                    UIMessage.assistant("keepthirdmarker").let { it.copy(parts = it.parts + sharedImage) },
                ),
                selectIndex = 2,
            ),
            UIMessage.user("lastnodemarker").let {
                it.copy(parts = it.parts + UIMessagePart.Image("file:///deleted-node.png"))
            }.toMessageNode(),
        ),
    ).copy(
        title = "Message deletion",
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
}
