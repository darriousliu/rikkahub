package me.rerere.rikkahub.ui.components.ai

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.rerere.rikkahub.data.datastore.Settings
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

    @Composable
    fun RenderAttachments(state: ChatInputState)

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

    @Composable
    override fun RenderAttachments(state: ChatInputState) = Unit

    @Composable
    override fun RenderVoiceAndSendActions(
        state: ChatInputState,
        loading: Boolean,
        sendAction: @Composable () -> Unit,
    ) {
        sendAction()
    }
}
