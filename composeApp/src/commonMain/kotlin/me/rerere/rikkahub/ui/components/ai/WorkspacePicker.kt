package me.rerere.rikkahub.ui.components.ai

import androidx.compose.runtime.Composable
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation

@Composable
expect fun WorkspacePicker(
    assistant: Assistant,
    conversation: Conversation,
    onUpdateAssistant: (Assistant) -> Unit,
    onUpdateConversation: (Conversation) -> Unit,
    onDismiss: () -> Unit,
)

@Composable
expect fun WorkspaceCwdPicker(
    assistant: Assistant,
    conversation: Conversation,
    onUpdateConversation: (Conversation) -> Unit,
)
