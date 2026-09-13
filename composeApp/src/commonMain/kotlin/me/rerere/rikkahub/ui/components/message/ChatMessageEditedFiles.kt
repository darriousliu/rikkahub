package me.rerere.rikkahub.ui.components.message

import androidx.compose.runtime.Composable
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Assistant

@Composable
internal expect fun EditedFilesList(parts: List<UIMessagePart>, assistant: Assistant?)
