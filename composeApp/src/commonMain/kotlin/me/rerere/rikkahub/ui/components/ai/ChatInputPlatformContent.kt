package me.rerere.rikkahub.ui.components.ai

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.db.entity.ManagedFileEntity
import me.rerere.rikkahub.service.SharedChatAttachmentStore
import me.rerere.rikkahub.ui.hooks.ChatInputState

/** Platform content embedded in the shared chat input. */
interface ChatInputPlatformContent {
    @Composable
    fun isImeVisible(): Boolean

    @Composable
    fun contentReceiverModifier(
        state: ChatInputState,
        settings: Settings,
    ): Modifier

    fun observeFiles(): Flow<List<ManagedFileEntity>>

    fun deleteChatFiles(locations: List<String>, scope: CoroutineScope)

    @Composable
    fun RenderVoiceAndSendActions(
        state: ChatInputState,
        loading: Boolean,
        sendAction: @Composable () -> Unit,
    )
}

object UnavailableChatInputPlatformContent : ChatInputPlatformContent {
    @Composable
    override fun isImeVisible(): Boolean = false

    @Composable
    override fun contentReceiverModifier(
        state: ChatInputState,
        settings: Settings,
    ): Modifier = Modifier

    override fun observeFiles(): Flow<List<ManagedFileEntity>> = flowOf(emptyList())

    override fun deleteChatFiles(locations: List<String>, scope: CoroutineScope) = Unit

    @Composable
    override fun RenderVoiceAndSendActions(
        state: ChatInputState,
        loading: Boolean,
        sendAction: @Composable () -> Unit,
    ) {
        sendAction()
    }
}

internal class SharedChatInputPlatformContent(
    private val attachmentStore: SharedChatAttachmentStore,
) : ChatInputPlatformContent by UnavailableChatInputPlatformContent {
    override fun deleteChatFiles(locations: List<String>, scope: CoroutineScope) {
        scope.launch { attachmentStore.delete(locations) }
    }
}
