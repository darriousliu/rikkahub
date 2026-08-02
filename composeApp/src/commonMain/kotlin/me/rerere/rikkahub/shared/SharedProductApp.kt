package me.rerere.rikkahub.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import me.rerere.ai.provider.ProviderManager
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.BooleanPreferenceStore
import me.rerere.rikkahub.data.datastore.StringPreferenceStore
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.event.AppEventBus
import me.rerere.rikkahub.platform.AnalyticsTracker
import me.rerere.rikkahub.platform.ChatNotificationManager
import me.rerere.rikkahub.platform.ChatNotificationPresenter
import me.rerere.rikkahub.platform.CrashReporter
import me.rerere.rikkahub.platform.ExternalUriOpener
import me.rerere.rikkahub.platform.NoOpMonitoring
import me.rerere.rikkahub.ui.components.message.ChatMessagePlatformActions
import me.rerere.rikkahub.ui.components.message.SharedChatMessagePlatformActions
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.richtext.RichTextPlatformActions
import me.rerere.rikkahub.ui.context.Navigator
import me.rerere.rikkahub.ui.hooks.CustomTtsState
import me.rerere.rikkahub.ui.hooks.rememberSharedCustomTtsState
import me.rerere.rikkahub.ui.pages.setting.ChatStorageSummaryProvider
import me.rerere.rikkahub.ui.pages.setting.UnavailableChatStorageSummaryProvider
import me.rerere.rikkahub.ui.theme.RikkahubTheme
import me.rerere.rikkahub.ui.theme.ChatFontRuntime
import me.rerere.rikkahub.ui.theme.UnavailableChatFontRuntime
import me.rerere.rikkahub.web.WebServerRuntime
import me.rerere.tts.model.PlaybackState
import me.rerere.tts.controller.PlatformAudioPlayer
import me.rerere.tts.provider.TTSManager
import me.rerere.tts.provider.TTSProvider
import me.rerere.tts.provider.TTSProviderSetting
import org.koin.compose.KoinApplication
import org.koin.dsl.koinConfiguration
import kotlin.uuid.Uuid

/**
 * Product entry used by non-Android shells while the remaining platform-only routes are migrated.
 */
@Composable
fun SharedProductApp(
    settingsStore: SettingsStore,
    database: AppDatabase,
    buildInfo: PlatformBuildInfo,
    externalUriOpener: ExternalUriOpener,
    webServerRuntime: WebServerRuntime,
    booleanPreferenceStore: BooleanPreferenceStore,
    stringPreferenceStore: StringPreferenceStore,
    chatFontRuntime: ChatFontRuntime = UnavailableChatFontRuntime,
    chatStorageSummaryProvider: ChatStorageSummaryProvider = UnavailableChatStorageSummaryProvider,
    analyticsTracker: AnalyticsTracker = NoOpMonitoring,
    crashReporter: CrashReporter = NoOpMonitoring,
    chatNotificationPresenter: ChatNotificationPresenter? = null,
    systemTtsProvider: TTSProvider<TTSProviderSetting.SystemTTS>? = null,
    platformAudioPlayer: PlatformAudioPlayer? = null,
    platformRoutes: PlatformRouteContent = SharedUnavailableRouteContent,
    chatMessagePlatformActions: ChatMessagePlatformActions? = null,
    richTextPlatformActions: @Composable (Navigator) -> RichTextPlatformActions = { RichTextPlatformActions() },
    startScreen: Screen? = null,
) {
    val appScope = rememberCoroutineScope()
    val eventBus = remember { AppEventBus() }
    val httpClient = remember { HttpClient() }
    val providerManager = remember(httpClient) { ProviderManager(httpClient) }
    val ttsManager = remember(httpClient, systemTtsProvider) {
        systemTtsProvider?.let { TTSManager(httpClient = httpClient, systemProvider = it) }
    }
    val notificationManager = remember(chatNotificationPresenter) {
        chatNotificationPresenter?.let { ChatNotificationManager() }
    }
    val resolvedChatMessagePlatformActions = remember(externalUriOpener, chatMessagePlatformActions) {
        chatMessagePlatformActions ?: SharedChatMessagePlatformActions(externalUriOpener)
    }
    val resolvedStartScreen by produceState<Screen?>(initialValue = startScreen, startScreen, stringPreferenceStore) {
        if (value == null) {
            val rememberedId = stringPreferenceStore.get(LAST_CONVERSATION_KEY)
                ?.let { stored -> runCatching { Uuid.parse(stored) }.getOrNull() }
            value = Screen.Chat((rememberedId ?: Uuid.random()).toString())
        }
    }
    val productModule = remember(
        settingsStore,
        database,
        buildInfo,
        externalUriOpener,
        webServerRuntime,
        booleanPreferenceStore,
        stringPreferenceStore,
        chatFontRuntime,
        chatStorageSummaryProvider,
        eventBus,
        providerManager,
        analyticsTracker,
        crashReporter,
        resolvedChatMessagePlatformActions,
    ) {
        sharedProductModule(
            settingsStore = settingsStore,
            database = database,
            buildInfo = buildInfo,
            externalUriOpener = externalUriOpener,
            webServerRuntime = webServerRuntime,
            booleanPreferenceStore = booleanPreferenceStore,
            stringPreferenceStore = stringPreferenceStore,
            chatFontRuntime = chatFontRuntime,
            chatStorageSummaryProvider = chatStorageSummaryProvider,
            httpClient = httpClient,
            providerManager = providerManager,
            appScope = appScope,
            analyticsTracker = analyticsTracker,
            crashReporter = crashReporter,
            eventBus = eventBus,
            chatMessagePlatformActions = resolvedChatMessagePlatformActions,
        )
    }
    val koinConfiguration = remember(productModule) {
        koinConfiguration { modules(productModule) }
    }

    DisposableEffect(httpClient) {
        onDispose { httpClient.close() }
    }
    DisposableEffect(notificationManager, chatNotificationPresenter, appScope, eventBus, settingsStore) {
        if (notificationManager != null && chatNotificationPresenter != null) {
            notificationManager.start(
                scope = appScope,
                eventBus = eventBus,
                settingsStore = settingsStore,
                presenter = chatNotificationPresenter,
            )
        }
        onDispose { notificationManager?.close() }
    }

    val initialScreen = resolvedStartScreen ?: return
    val ttsState = if (ttsManager != null && platformAudioPlayer != null) {
        rememberSharedCustomTtsState(
            settingsStore = settingsStore,
            ttsManager = ttsManager,
            audioPlayer = platformAudioPlayer,
        )
    } else {
        UnavailableCustomTtsState
    }

    KoinApplication(configuration = koinConfiguration) {
        RikkahubTheme {
            RikkaHubApp {
                ProductNavigationHost(
                    startScreen = initialScreen,
                    ttsState = ttsState,
                    platformRoutes = platformRoutes,
                    richTextPlatformActions = richTextPlatformActions,
                )
            }
        }
    }
}

private const val LAST_CONVERSATION_KEY = "lastConversationId"

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
