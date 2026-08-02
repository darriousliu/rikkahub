package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.service.ChatError
import me.rerere.rikkahub.ui.components.ai.completion.ChatCompletionProvider
import me.rerere.rikkahub.ui.hooks.ChatInputState
import kotlin.uuid.Uuid

/** Platform operations and Android-only content embedded in the shared chat page. */
interface ChatPagePlatformContent {
    suspend fun importInitialFiles(files: List<String>): List<UIMessagePart>

    fun completionProviders(
        assistant: Assistant,
        conversation: Conversation,
    ): List<ChatCompletionProvider>

    @Composable
    fun RegisterBackHandler(enabled: Boolean, onBack: () -> Unit)

    @Composable
    fun volumeKeyEventSource(): VolumeKeyEventSource?

    @Composable
    fun isScrollCaptureInProgress(): Boolean

    @Composable
    fun RenderDrawerHeader(vm: ChatVM, settings: Settings)

    @Composable
    fun RenderFilesPicker(
        inputState: ChatInputState,
        setting: Settings,
        conversation: Conversation,
        assistant: Assistant,
        vm: ChatVM,
        onDismiss: () -> Unit,
    )

    @Composable
    fun RenderExport(presentation: ChatExportPresentation)

    @Composable
    fun RenderErrors(
        errors: List<ChatError>,
        onDismissError: (Uuid) -> Unit,
        onClearAllErrors: () -> Unit,
        modifier: Modifier,
    )

    @Composable
    fun RenderLoading(modifier: Modifier)
}

object UnavailableChatPagePlatformContent : ChatPagePlatformContent {
    override suspend fun importInitialFiles(files: List<String>): List<UIMessagePart> = emptyList()

    override fun completionProviders(
        assistant: Assistant,
        conversation: Conversation,
    ): List<ChatCompletionProvider> = emptyList()

    @Composable
    override fun RegisterBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit

    @Composable
    override fun volumeKeyEventSource(): VolumeKeyEventSource? = null

    @Composable
    override fun isScrollCaptureInProgress(): Boolean = false

    @Composable
    override fun RenderDrawerHeader(vm: ChatVM, settings: Settings) = Unit

    @Composable
    override fun RenderFilesPicker(
        inputState: ChatInputState,
        setting: Settings,
        conversation: Conversation,
        assistant: Assistant,
        vm: ChatVM,
        onDismiss: () -> Unit,
    ) = Unit

    @Composable
    override fun RenderExport(presentation: ChatExportPresentation) = Unit

    @Composable
    override fun RenderErrors(
        errors: List<ChatError>,
        onDismissError: (Uuid) -> Unit,
        onClearAllErrors: () -> Unit,
        modifier: Modifier,
    ) {
        Column(modifier = modifier) {
            errors.forEach { error -> Text(error.error.message ?: "Unknown error") }
        }
    }

    @Composable
    override fun RenderLoading(modifier: Modifier) {
        CircularProgressIndicator(modifier = modifier)
    }
}
