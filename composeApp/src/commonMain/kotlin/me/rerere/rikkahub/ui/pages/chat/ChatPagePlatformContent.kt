package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import kotlinx.coroutines.launch
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.service.SharedChatAttachmentStore
import me.rerere.rikkahub.ui.components.ai.completion.ChatCompletionProvider
import me.rerere.rikkahub.ui.hooks.ChatInputState
import androidx.compose.material3.rememberModalBottomSheetState
import io.github.vinceglb.filekit.PlatformFile
import me.rerere.rikkahub.ui.components.ai.FilesPicker
import org.koin.compose.koinInject

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
    fun RenderExport(
        visible: Boolean,
        onDismissRequest: () -> Unit,
        conversation: Conversation,
        selectedMessages: List<UIMessage>,
    )
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
    override fun RenderExport(
        visible: Boolean,
        onDismissRequest: () -> Unit,
        conversation: Conversation,
        selectedMessages: List<UIMessage>,
    ) = Unit
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
        var showInjectionSheet by remember { mutableStateOf(false) }
        var showCompressDialog by remember { mutableStateOf(false) }

        fun dismissAll() {
            showInjectionSheet = false
            showCompressDialog = false
            onDismiss()
        }

        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(sheetState = sheetState, onDismissRequest = ::dismissAll) {
            // Keep the sheet's view controller alive until the native picker returns.
            val onFilesSelected: (List<PlatformFile>?) -> Unit = { selectedFiles ->
                if (selectedFiles == null) {
                    dismissAll()
                } else {
                    scope.launch {
                        inputState.messageContent += attachmentStore.import(selectedFiles)
                        dismissAll()
                    }
                }
            }
            val mode = FileKitMode.Multiple(maxItems = MAX_ATTACHMENT_COUNT)
            val filePicker = rememberFilePickerLauncher(
                type = FileKitType.File(extensions = null),
                mode = mode,
                onResult = onFilesSelected,
            )
            val imagePicker = rememberFilePickerLauncher(
                type = FileKitType.Image,
                mode = mode,
                onResult = onFilesSelected,
            )
            val videoPicker = rememberFilePickerLauncher(
                type = FileKitType.Video,
                mode = mode,
                onResult = onFilesSelected,
            )
            val audioPicker = rememberFilePickerLauncher(
                type = FileKitType.File("mp3", "m4a", "wav", "ogg", "aac", "flac", "opus", "aiff", "amr"),
                mode = mode,
                onResult = onFilesSelected,
            )
            FilesPicker(
                conversation = conversation,
                state = inputState,
                assistant = assistant,
                mcpManager = koinInject(),
                onCompressContext = vm::handleCompressContext,
                onUpdateAssistant = {
                    vm.updateSettings(
                        setting.copy(
                            assistants = setting.assistants.map { assistant ->
                                if (assistant.id == it.id) it else assistant
                            },
                        ),
                    )
                },
                onUpdateConversation = {
                    vm.updateConversation(it)
                    vm.saveConversationAsync()
                },
                showInjectionSheet = showInjectionSheet,
                onShowInjectionSheetChange = { showInjectionSheet = it },
                showCompressDialog = showCompressDialog,
                onShowCompressDialogChange = { showCompressDialog = it },
                onDismiss = ::dismissAll,
                onTakePic = null,
                onPickImage = { imagePicker.launch() },
                onPickVideo = { videoPicker.launch() },
                onPickAudio = { audioPicker.launch() },
                onPickFile = { filePicker.launch() },
            )
        }
    }

    private companion object {
        const val MAX_ATTACHMENT_COUNT = 16
    }
}
