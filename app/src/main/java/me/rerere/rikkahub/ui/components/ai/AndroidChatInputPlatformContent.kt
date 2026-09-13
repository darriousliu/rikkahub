package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.content.MediaType
import androidx.compose.foundation.content.ReceiveContentListener
import androidx.compose.foundation.content.consume
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.content.hasMediaType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import me.rerere.asr.ASRStatus
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.files.createChatFilesByContents
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.ui.hooks.ChatInputState
import me.rerere.rikkahub.utils.SoundEffectPlayer

class AndroidChatInputPlatformContent(
    private val filesManager: FilesManager,
    private val soundEffectPlayer: SoundEffectPlayer,
) : ChatInputPlatformContent {
    @Composable
    override fun contentReceiverModifier(
        state: ChatInputState,
        settings: Settings,
    ): Modifier {
        val listener = remember(
            settings.displaySetting.pasteLongTextAsFile,
            settings.displaySetting.pasteLongTextThreshold,
        ) {
            ReceiveContentListener { transferableContent ->
                when {
                    transferableContent.hasMediaType(MediaType.Image) -> {
                        transferableContent.consume { item ->
                            val uri = item.uri
                            if (uri != null) {
                                state.addImages(
                                    filesManager.createChatFilesByContents(listOf(uri)).map { it.toString() },
                                )
                            }
                            uri != null
                        }
                    }

                    settings.displaySetting.pasteLongTextAsFile &&
                        transferableContent.hasMediaType(MediaType.Text) -> {
                        transferableContent.consume { item ->
                            val text = item.text?.toString()
                            if (text != null && text.length > settings.displaySetting.pasteLongTextThreshold) {
                                state.addFiles(listOf(filesManager.createChatTextFile(text)))
                                true
                            } else {
                                false
                            }
                        }
                    }

                    else -> transferableContent
                }
            }
        }
        return Modifier.contentReceiver(listener)
    }

    override fun preloadAsrSounds() {
        soundEffectPlayer.preload(R.raw.asr_start, R.raw.asr_stop)
    }

    override fun playAsrSound(status: ASRStatus) {
        when (status) {
            ASRStatus.Listening -> soundEffectPlayer.play(R.raw.asr_start)
            ASRStatus.Stopping -> soundEffectPlayer.play(R.raw.asr_stop)
            else -> Unit
        }
    }

    @Composable
    override fun KeepScreenOn() {
        me.rerere.rikkahub.ui.components.ui.KeepScreenOn()
    }
}
