package me.rerere.rikkahub.platform

import kotlin.uuid.Uuid

public sealed interface ChatNotificationPhase {
    public data class Tool(
        val toolName: String,
        val inputPreview: String,
    ) : ChatNotificationPhase

    public data class Thinking(val preview: String) : ChatNotificationPhase

    public data class Writing(val preview: String) : ChatNotificationPhase

    public data object Starting : ChatNotificationPhase
}

public data class ChatLiveUpdateNotification(
    val conversationId: Uuid,
    val senderName: String,
    val phase: ChatNotificationPhase,
)

public interface ChatNotificationPresenter {
    public fun showLiveUpdate(notification: ChatLiveUpdateNotification)

    public fun showGenerationCompleted(
        conversationId: Uuid,
        senderName: String,
        contentPreview: String,
    )

    public fun cancelLiveUpdate(conversationId: Uuid)

    public fun close() {}
}

internal expect fun observeChatNotificationForeground(onChanged: (Boolean) -> Unit): () -> Unit

internal expect fun notificationTimeMillis(): Long
