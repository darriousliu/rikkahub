package me.rerere.rikkahub.ui.components.message

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Assistant

val LocalEditedFilesContent = staticCompositionLocalOf<@Composable (List<UIMessagePart>, Assistant?) -> Unit> {
    { _, _ -> }
}
