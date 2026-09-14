package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.runtime.CompositionLocalContext
import androidx.compose.ui.unit.Density
import coil3.PlatformContext
import kotlinx.coroutines.CoroutineScope
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Conversation

internal actual suspend fun exportToImage(
    context: PlatformContext,
    compositionLocalContext: CompositionLocalContext,
    scope: CoroutineScope,
    density: Density,
    conversation: Conversation,
    messages: List<UIMessage>,
    settings: Settings,
    options: ImageExportOptions,
) {
    error("Image export is not yet available on iOS")
}
