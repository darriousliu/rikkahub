package me.rerere.rikkahub.ui.components.message

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.context.Navigator

/** Platform operations used by the shared chat message renderer. */
interface ChatMessagePlatformActions {
    fun openAttachment(uri: String): Result<Unit>

    fun openMarkdownPreview(
        markdown: String,
        colorScheme: ColorScheme,
        navigator: Navigator,
    ): Result<Unit>

    @Composable
    fun RenderEditedFiles(
        parts: List<UIMessagePart>,
        assistant: Assistant?,
    )
}

/** Safe fallback for platforms where Android-only message actions are unavailable. */
object UnavailableChatMessagePlatformActions : ChatMessagePlatformActions {
    override fun openAttachment(uri: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Opening local attachments is unavailable"))

    override fun openMarkdownPreview(
        markdown: String,
        colorScheme: ColorScheme,
        navigator: Navigator,
    ): Result<Unit> = Result.failure(UnsupportedOperationException("Markdown WebView preview is unavailable"))

    @Composable
    override fun RenderEditedFiles(
        parts: List<UIMessagePart>,
        assistant: Assistant?,
    ) = Unit
}
