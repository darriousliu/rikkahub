package me.rerere.rikkahub.data.event

import me.rerere.ai.ui.UIMessage
import kotlin.uuid.Uuid

public sealed class AppEvent {
    public data class Speak(val text: String) : AppEvent()

    public data object OpenUsageAccessSettings : AppEvent()

    /** MCP OAuth 授权完成后经回调会话回传的结果。 */
    public data class McpOAuthCallback(
        val state: String?,
        val code: String?,
        val error: String?,
    ) : AppEvent()

    /** 聊天生成过程中的流式更新，由通知策略消费。 */
    public data class ChatGenerationUpdate(
        val conversationId: Uuid,
        val lastMessage: UIMessage,
        val senderName: String,
    ) : AppEvent()

    /**
     * 聊天生成结束（完成、失败或取消）。
     * [contentPreview] 为 null 时仅取消 Live Update 通知，不发送完成通知。
     */
    public data class ChatGenerationEnded(
        val conversationId: Uuid,
        val senderName: String,
        val contentPreview: String?,
    ) : AppEvent()
}
