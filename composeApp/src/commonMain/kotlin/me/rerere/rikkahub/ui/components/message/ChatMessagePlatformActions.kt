package me.rerere.rikkahub.ui.components.message

import androidx.compose.runtime.Composable
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.platform.ExternalUriOpener

/** Platform operations used by the shared chat message renderer. */
interface ChatMessagePlatformActions {
    fun openAttachment(uri: String): Result<Unit>


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


    @Composable
    override fun RenderEditedFiles(
        parts: List<UIMessagePart>,
        assistant: Assistant?,
    ) = Unit
}

internal class SharedChatMessagePlatformActions(
    private val externalUriOpener: ExternalUriOpener,
) : ChatMessagePlatformActions {
    override fun openAttachment(uri: String): Result<Unit> = externalUriOpener.open(uri)


    @Composable
    override fun RenderEditedFiles(
        parts: List<UIMessagePart>,
        assistant: Assistant?,
    ) = Unit
}
