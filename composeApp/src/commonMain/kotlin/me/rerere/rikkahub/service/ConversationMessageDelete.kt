package me.rerere.rikkahub.service

import me.rerere.rikkahub.data.model.Conversation
import kotlin.uuid.Uuid

fun buildConversationAfterMessageDelete(
    conversation: Conversation,
    messageId: Uuid,
): Conversation? {
    val targetNodeIndex = conversation.messageNodes.indexOfFirst { node ->
        node.messages.any { it.id == messageId }
    }
    if (targetNodeIndex == -1) {
        return null
    }

    val updatedNodes = conversation.messageNodes.mapIndexedNotNull { index, node ->
        if (index != targetNodeIndex) {
            return@mapIndexedNotNull node
        }

        val nextMessages = node.messages.filterNot { it.id == messageId }
        if (nextMessages.isEmpty()) {
            return@mapIndexedNotNull null
        }

        val nextSelectIndex = node.selectIndex.coerceAtMost(nextMessages.lastIndex)
        node.copy(
            messages = nextMessages,
            selectIndex = nextSelectIndex,
        )
    }

    return conversation.copy(messageNodes = updatedNodes)
}
