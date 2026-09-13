package me.rerere.rikkahub.ui.components.ai

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.rerere.asr.ASRStatus
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.ui.hooks.ChatInputState

/** Android app integrations that use ClipData, raw sound resources and Activity window flags. */
interface ChatInputPlatformContent {
    @Composable
    fun contentReceiverModifier(state: ChatInputState, settings: Settings): Modifier = Modifier

    fun preloadAsrSounds() = Unit

    fun playAsrSound(status: ASRStatus) = Unit

    @Composable
    fun KeepScreenOn() = Unit
}

object UnavailableChatInputPlatformContent : ChatInputPlatformContent
