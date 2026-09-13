package me.rerere.rikkahub.ui.components.ai.completion

import androidx.compose.runtime.Composable
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation

@Composable
actual fun rememberWorkspaceCompletionProviders(
    assistant: Assistant,
    conversation: Conversation,
): List<ChatCompletionProvider> = emptyList()
