package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.runtime.Composable
import io.github.vinceglb.filekit.PlatformFile
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.ui.components.ai.completion.ChatCompletionProvider
import me.rerere.rikkahub.ui.hooks.ChatInputState

/** Android integrations owned by the app module: workspace, camera, capture and export. */
interface ChatPagePlatformContent {
    fun completionProviders(assistant: Assistant, conversation: Conversation): List<ChatCompletionProvider> = emptyList()

    @Composable
    fun volumeKeyEventSource(): VolumeKeyEventSource? = null

    @Composable
    fun isScrollCaptureInProgress(): Boolean = false

    @Composable
    fun RenderDrawerHeader(vm: ChatVM, settings: Settings) = Unit

    @Composable
    fun rememberCameraLauncher(
        inputState: ChatInputState,
        skipCropImage: Boolean,
        onDismiss: () -> Unit,
    ): (() -> Unit)? = null

    @Composable
    fun rememberImageCropLauncher(
        inputState: ChatInputState,
        onDismiss: () -> Unit,
    ): ((PlatformFile) -> Unit)? = null

    @Composable
    fun WorkspacePicker(
        assistant: Assistant,
        conversation: Conversation,
        onUpdateAssistant: (Assistant) -> Unit,
        onUpdateConversation: (Conversation) -> Unit,
        onDismiss: () -> Unit,
    ) = Unit

    @Composable
    fun WorkspaceCwdPicker(
        assistant: Assistant,
        conversation: Conversation,
        onUpdateConversation: (Conversation) -> Unit,
    ) = Unit

    @Composable
    fun RenderExport(
        visible: Boolean,
        onDismissRequest: () -> Unit,
        conversation: Conversation,
        selectedMessages: List<UIMessage>,
    ) = Unit
}

object UnavailableChatPagePlatformContent : ChatPagePlatformContent
