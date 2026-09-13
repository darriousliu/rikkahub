package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.runtime.Composable
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.model.Conversation

@Composable
expect fun ChatExportSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    conversation: Conversation,
    selectedMessages: List<UIMessage>,
)
