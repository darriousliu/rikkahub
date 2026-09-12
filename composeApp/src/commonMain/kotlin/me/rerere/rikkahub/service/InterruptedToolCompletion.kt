package me.rerere.rikkahub.service

import me.rerere.ai.ui.ToolApprovalState
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.finishPendingTools
import kotlin.uuid.Uuid

private fun cancelToolByUser(tool: UIMessagePart.Tool): UIMessagePart.Tool {
    return tool.copy(
        output = listOf(
            UIMessagePart.Text(
                """{"status":"cancelled","error":"Generation cancelled by user before tool execution completed."}"""
            )
        ),
        approvalState = ToolApprovalState.Denied("Generation cancelled by user")
    )
}

suspend fun ChatService.finishInterruptedPendingTools(conversationId: Uuid) {
    val currentConversation = getConversationFlow(conversationId).value
    val lastNode = currentConversation.messageNodes.lastOrNull() ?: return
    val lastMessage = lastNode.currentMessage
    val updatedMessage = lastMessage.finishPendingTools(::cancelToolByUser)
    if (updatedMessage == lastMessage) {
        return
    }

    val updatedConversation = currentConversation.copy(
        messageNodes = currentConversation.messageNodes.dropLast(1) + lastNode.copy(
            messages = lastNode.messages.map { message ->
                if (message.id == lastMessage.id) updatedMessage else message
            }
        )
    )
    saveConversation(conversationId, updatedConversation)
}
