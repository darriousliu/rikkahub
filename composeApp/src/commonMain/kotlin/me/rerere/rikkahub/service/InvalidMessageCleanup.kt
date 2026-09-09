package me.rerere.rikkahub.service

import me.rerere.ai.ui.canResumeToolExecution
import me.rerere.rikkahub.data.model.Conversation

fun buildConversationAfterInvalidMessageCleanup(conversation: Conversation): Conversation {
    var messagesNodes = conversation.messageNodes

    // 移除无效 tool (未执行的 Tool)
    messagesNodes = messagesNodes.mapIndexed { _, node ->
        // Check for Tool type with non-executed tools
        val hasPendingTools = node.currentMessage.getTools().any { !it.isExecuted }

        if (hasPendingTools) {
            // Keep messages that are ready to resume, such as approved/denied/answered tools.
            val hasResumableTool = node.currentMessage.getTools().any {
                !it.isExecuted && it.approvalState.canResumeToolExecution()
            }
            if (hasResumableTool) {
                return@mapIndexed node
            }

            // If all tools are executed, it's valid
            val allToolsExecuted = node.currentMessage.getTools().all { it.isExecuted }
            if (allToolsExecuted && node.currentMessage.getTools().isNotEmpty()) {
                return@mapIndexed node
            }

            // Remove messages that still have unresolved tool approvals.
            return@mapIndexed node.copy(
                messages = node.messages.filter { it.id != node.currentMessage.id },
                selectIndex = node.selectIndex - 1
            )
        }
        node
    }

    // 更新index
    messagesNodes = messagesNodes.map { node ->
        if (node.messages.isNotEmpty() && node.selectIndex !in node.messages.indices) {
            node.copy(selectIndex = 0)
        } else {
            node
        }
    }

    // 移除无效消息
    messagesNodes = messagesNodes.filter { it.messages.isNotEmpty() }

    return conversation.copy(messageNodes = messagesNodes)
}
