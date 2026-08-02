package me.rerere.rikkahub.ui.hooks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getSelectedTTSProvider
import me.rerere.rikkahub.utils.stripMarkdown
import me.rerere.tts.controller.PlatformAudioPlayer
import me.rerere.tts.controller.TtsController
import me.rerere.tts.model.PlaybackState
import me.rerere.tts.provider.TTSManager
import me.rerere.tts.provider.TTSProviderSetting

@Composable
internal fun rememberSharedCustomTtsState(
    settingsStore: SettingsStore,
    ttsManager: TTSManager,
    audioPlayer: PlatformAudioPlayer,
): CustomTtsState {
    val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
    val state = remember(settingsStore, ttsManager, audioPlayer) {
        SharedCustomTtsState(
            ttsManager = ttsManager,
            audioPlayer = audioPlayer,
        )
    }

    DisposableEffect(
        state,
        settings.selectedTTSProviderId,
        settings.ttsProviders,
        settings.defaultTTSPlaybackSpeed,
    ) {
        state.updateProvider(settings.getSelectedTTSProvider())
        state.setSpeed(settings.defaultTTSPlaybackSpeed)
        onDispose { }
    }
    DisposableEffect(state) {
        onDispose(state::cleanup)
    }
    return state
}

private class SharedCustomTtsState(
    ttsManager: TTSManager,
    audioPlayer: PlatformAudioPlayer,
) : CustomTtsState {
    private val controller = TtsController(
        ttsManager = ttsManager,
        audio = audioPlayer,
    )

    override val isAvailable: StateFlow<Boolean> = controller.isAvailable
    override val isSpeaking: StateFlow<Boolean> = controller.isSpeaking
    override val error: StateFlow<String?> = controller.error
    override val currentChunk: StateFlow<Int> = controller.currentChunk
    override val totalChunks: StateFlow<Int> = controller.totalChunks
    override val playbackState: StateFlow<PlaybackState> = controller.playbackState

    fun updateProvider(provider: TTSProviderSetting?) = controller.setProvider(provider)

    override fun speak(text: String, flushCalled: Boolean) {
        controller.speak(text.stripMarkdown(), flushCalled)
    }

    override fun stop() = controller.stop()

    override fun pause() = controller.pause()

    override fun resume() = controller.resume()

    override fun skipNext() = controller.skipNext()

    override fun fastForward(ms: Long) = controller.fastForward(ms)

    override fun setSpeed(speed: Float) = controller.setSpeed(speed)

    override fun cleanup() = controller.dispose()
}
