package me.rerere.rikkahub.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import me.rerere.ai.provider.ProviderManager
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.BooleanPreferenceStore
import me.rerere.rikkahub.data.event.AppEventBus
import me.rerere.rikkahub.platform.ExternalUriOpener
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.hooks.CustomTtsState
import me.rerere.rikkahub.ui.pages.setting.ChatStorageSummaryProvider
import me.rerere.rikkahub.ui.pages.setting.SettingVM
import me.rerere.rikkahub.ui.pages.setting.UnavailableChatStorageSummaryProvider
import me.rerere.rikkahub.ui.theme.RikkahubTheme
import me.rerere.rikkahub.web.WebServerRuntime
import me.rerere.tts.model.PlaybackState
import org.koin.compose.KoinApplication
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.koinConfiguration
import org.koin.dsl.module

/**
 * Product entry used by non-Android shells while the remaining platform-only routes are migrated.
 */
@Composable
fun SharedProductApp(
    settingsStore: SettingsStore,
    buildInfo: PlatformBuildInfo,
    externalUriOpener: ExternalUriOpener,
    webServerRuntime: WebServerRuntime,
    booleanPreferenceStore: BooleanPreferenceStore,
    chatStorageSummaryProvider: ChatStorageSummaryProvider = UnavailableChatStorageSummaryProvider,
    startScreen: Screen = Screen.Setting,
) {
    val eventBus = remember { AppEventBus() }
    val httpClient = remember { HttpClient() }
    val providerManager = remember(httpClient) { ProviderManager(httpClient) }
    val productModule = remember(
        settingsStore,
        buildInfo,
        externalUriOpener,
        webServerRuntime,
        booleanPreferenceStore,
        chatStorageSummaryProvider,
        eventBus,
        providerManager,
    ) {
        module {
            single { settingsStore }
            single { buildInfo }
            single { externalUriOpener }
            single { webServerRuntime }
            single { booleanPreferenceStore }
            single { chatStorageSummaryProvider }
            single { eventBus }
            single { providerManager }
            viewModelOf(::SettingVM)
        }
    }
    val koinConfiguration = remember(productModule) {
        koinConfiguration { modules(productModule) }
    }

    DisposableEffect(httpClient) {
        onDispose { httpClient.close() }
    }

    KoinApplication(configuration = koinConfiguration) {
        RikkahubTheme {
            RikkaHubApp {
                ProductNavigationHost(
                    startScreen = startScreen,
                    ttsState = UnavailableCustomTtsState,
                    platformRoutes = SharedUnavailableRouteContent,
                )
            }
        }
    }
}

private object SharedUnavailableRouteContent : PlatformRouteContent {
    @Composable
    override fun Render(screen: Screen) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BackButton()
            Text(
                text = screen::class.simpleName ?: "Unavailable route",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = "This feature is not available on ${currentPlatformKind.displayName} yet.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

private object UnavailableCustomTtsState : CustomTtsState {
    override val isAvailable: StateFlow<Boolean> = MutableStateFlow(false)
    override val isSpeaking: StateFlow<Boolean> = MutableStateFlow(false)
    override val error: StateFlow<String?> = MutableStateFlow(null)
    override val currentChunk: StateFlow<Int> = MutableStateFlow(0)
    override val totalChunks: StateFlow<Int> = MutableStateFlow(0)
    override val playbackState: StateFlow<PlaybackState> = MutableStateFlow(PlaybackState())

    override fun speak(text: String, flushCalled: Boolean) = Unit
    override fun stop() = Unit
    override fun pause() = Unit
    override fun resume() = Unit
    override fun skipNext() = Unit
    override fun fastForward(ms: Long) = Unit
    override fun setSpeed(speed: Float) = Unit
    override fun cleanup() = Unit
}
