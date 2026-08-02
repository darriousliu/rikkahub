package me.rerere.rikkahub.ui.components.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.content.MediaType
import androidx.compose.foundation.content.ReceiveContentListener
import androidx.compose.foundation.content.consume
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.content.hasMediaType
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.dokar.sonner.ToastType
import me.rerere.asr.ASRStatus
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.ui.components.ui.permission.PermissionManager
import me.rerere.rikkahub.ui.components.ui.permission.PermissionRecordAudio
import me.rerere.rikkahub.ui.components.ui.permission.rememberPermissionState
import me.rerere.rikkahub.ui.context.LocalASRState
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.ChatInputState
import me.rerere.rikkahub.utils.SoundEffectPlayer

class AndroidChatInputPlatformContent(
    private val filesManager: FilesManager,
    private val soundEffectPlayer: SoundEffectPlayer,
) : ChatInputPlatformContent {
    @Composable
    override fun isImeVisible(): Boolean = WindowInsets.isImeVisible

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

    @Composable
    override fun RenderAttachments(state: ChatInputState) {
        MediaFileInputRow(state)
    }

    @Composable
    override fun RenderVoiceAndSendActions(
        state: ChatInputState,
        loading: Boolean,
        sendAction: @Composable () -> Unit,
    ) {
        val toaster = LocalToaster.current
        val asr = LocalASRState.current
        val asrState by asr.state.collectAsState()
        val hapticFeedback = LocalHapticFeedback.current
        val asrPermission = rememberPermissionState(PermissionRecordAudio)
        PermissionManager(permissionState = asrPermission)
        var asrBaseText by remember { mutableStateOf("") }

        LaunchedEffect(Unit) {
            soundEffectPlayer.preload(R.raw.asr_start, R.raw.asr_stop)
        }
        LaunchedEffect(asrState.status) {
            when (asrState.status) {
                ASRStatus.Listening -> {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                    soundEffectPlayer.play(R.raw.asr_start)
                }

                ASRStatus.Stopping -> {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
                    soundEffectPlayer.play(R.raw.asr_stop)
                }

                else -> Unit
            }
        }
        LaunchedEffect(asrState.errorMessage) {
            asrState.errorMessage?.takeIf { it.isNotBlank() }?.let { message ->
                toaster.show(message = message, type = ToastType.Error)
            }
        }

        if (asrState.isAvailable || asrState.isRecording) {
            AsrButton(
                state = asrState,
                onClick = {
                    when (asrState.status) {
                        ASRStatus.Listening -> asr.stop()
                        ASRStatus.Idle, ASRStatus.Error -> {
                            if (!asrPermission.allRequiredPermissionsGranted) {
                                asrPermission.requestPermissions()
                            } else {
                                asrBaseText = state.textContent.text.toString()
                                asr.start { transcript ->
                                    val spacer = if (asrBaseText.isBlank() || transcript.isBlank()) "" else " "
                                    state.setMessageText(asrBaseText + spacer + transcript)
                                }
                            }
                        }

                        ASRStatus.Connecting, ASRStatus.Stopping -> Unit
                    }
                },
            )
        }

        AnimatedVisibility(
            visible = !asrState.isRecording,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
        ) {
            if (loading) {
                me.rerere.rikkahub.ui.components.ui.KeepScreenOn()
            }
            sendAction()
        }
    }
}
