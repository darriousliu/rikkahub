package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import kotlinx.coroutines.launch
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.service.SharedChatAttachmentStore
import me.rerere.rikkahub.generated.resources.Res
import me.rerere.rikkahub.generated.resources.chat_page_compress_context
import me.rerere.rikkahub.ui.components.ai.SharedCompressContextDialog
import me.rerere.rikkahub.ui.components.ai.completion.ChatCompletionProvider
import me.rerere.rikkahub.ui.hooks.ChatInputState
import org.jetbrains.compose.resources.stringResource

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
    override fun RenderLoading(modifier: Modifier) {
        CircularProgressIndicator(modifier = modifier)
    }
}

internal class SharedChatPagePlatformContent(
    private val attachmentStore: SharedChatAttachmentStore,
) : ChatPagePlatformContent by UnavailableChatPagePlatformContent {
    override suspend fun importInitialFiles(files: List<String>): List<UIMessagePart> =
        attachmentStore.importLocations(files)

    @Composable
    override fun RenderFilesPicker(
        inputState: ChatInputState,
        setting: Settings,
        conversation: Conversation,
        assistant: Assistant,
        vm: ChatVM,
        onDismiss: () -> Unit,
    ) {
        val scope = rememberCoroutineScope()
        var showCompression by remember { mutableStateOf(false) }
        when {
            showCompression -> SharedCompressContextDialog(
                onDismiss = onDismiss,
                onConfirm = vm::handleCompressContext,
            )
            else -> ModalBottomSheet(onDismissRequest = onDismiss) {
                // Keep the sheet's view controller alive until the native picker returns.
                val picker = rememberFilePickerLauncher(
                    type = FileKitType.File(extensions = null),
                    mode = FileKitMode.Multiple(maxItems = MAX_ATTACHMENT_COUNT),
                ) { selectedFiles ->
                    if (selectedFiles == null) {
                        onDismiss()
                    } else {
                        scope.launch {
                            inputState.messageContent += attachmentStore.import(selectedFiles)
                            onDismiss()
                        }
                    }
                }
                Column(modifier = Modifier.padding(16.dp)) {
                    TextButton(
                        onClick = { picker.launch() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("添加附件")
                    }
                    TextButton(
                        onClick = { showCompression = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(Res.string.chat_page_compress_context))
                    }
                }
            }
        }
    }

    private companion object {
        const val MAX_ATTACHMENT_COUNT = 16
    }
}
