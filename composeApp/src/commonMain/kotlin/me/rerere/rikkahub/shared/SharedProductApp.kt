package me.rerere.rikkahub.shared

import androidx.compose.runtime.LaunchedEffect
import org.koin.compose.koinInject
import me.rerere.rikkahub.data.files.FilesManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.cacheDir
import io.github.vinceglb.filekit.div
import io.github.vinceglb.filekit.filesDir
import io.github.vinceglb.filekit.toKotlinxIoPath
import korlibs.template.KorteTemplates
import me.rerere.search.SearchService
import kotlinx.coroutines.CoroutineScope
import kotlinx.io.files.Path
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.util.KeyRoulette
import me.rerere.ai.util.persistentLru
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.AppRoutes
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.BooleanPreferenceStore
import me.rerere.rikkahub.data.datastore.StringPreferenceStore
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.fts.MessageFtsDialect
import me.rerere.rikkahub.data.db.fts.MessageFtsManager
import me.rerere.rikkahub.data.event.AppEventBus
import me.rerere.rikkahub.data.ai.tools.local.LocalTools
import me.rerere.rikkahub.data.ai.transformers.DocumentTextExtractor
import me.rerere.rikkahub.data.ai.transformers.UnsupportedDocumentTextExtractor
import me.rerere.rikkahub.data.sync.BackupFileLayout
import me.rerere.rikkahub.di.appModule
import me.rerere.rikkahub.di.dataSourceModule
import me.rerere.rikkahub.di.repositoryModule
import me.rerere.rikkahub.di.viewModelModule
import me.rerere.rikkahub.platform.AnalyticsTracker
import me.rerere.rikkahub.service.ChatNotificationManager
import me.rerere.rikkahub.platform.ChatNotificationPresenter
import me.rerere.rikkahub.platform.CrashReporter
import me.rerere.rikkahub.platform.ExternalUriOpener
import me.rerere.rikkahub.platform.FileKitFileCleaner
import me.rerere.rikkahub.platform.OAuthCallbackSessionFactory
import me.rerere.rikkahub.ui.components.ai.ChatInputPlatformContent
import me.rerere.rikkahub.ui.components.ai.UnavailableChatInputPlatformContent
import me.rerere.rikkahub.ui.pages.chat.ChatPagePlatformContent
import me.rerere.rikkahub.ui.pages.chat.UnavailableChatPagePlatformContent
import me.rerere.rikkahub.ui.hooks.rememberSharedCustomTtsState
import me.rerere.rikkahub.ui.theme.RikkahubTheme
import me.rerere.rikkahub.ui.theme.ChatFontRuntime
import me.rerere.rikkahub.ui.theme.UnavailableChatFontRuntime
import me.rerere.rikkahub.web.WebServerRuntime
import me.rerere.tts.controller.PlatformAudioPlayer
import me.rerere.tts.provider.TTSManager
import me.rerere.tts.provider.TTSProvider
import me.rerere.tts.provider.TTSProviderSetting
import org.koin.compose.KoinApplication
import org.koin.dsl.koinConfiguration
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.uuid.Uuid

/** Compose entry shared by the iOS and desktop application shells. */
@Composable
fun SharedProductApp(
    settingsStore: SettingsStore,
    templateEngine: KorteTemplates,
    database: AppDatabase,
    buildInfo: PlatformBuildInfo,
    externalUriOpener: ExternalUriOpener,
    webServerRuntime: WebServerRuntime,
    booleanPreferenceStore: BooleanPreferenceStore,
    stringPreferenceStore: StringPreferenceStore,
    analyticsTracker: AnalyticsTracker,
    crashReporter: CrashReporter,
    chatNotificationPresenter: ChatNotificationPresenter,
    systemTtsProvider: TTSProvider<TTSProviderSetting.SystemTTS>,
    platformAudioPlayer: PlatformAudioPlayer,
    startScreen: Screen? = null,
    backupFileLayout: BackupFileLayout,
    oauthCallbackSessionFactory: OAuthCallbackSessionFactory,
) {
    val appScope = rememberCoroutineScope()
    val eventBus = remember { AppEventBus() }
    val keyRoulette = remember {
        KeyRoulette.persistentLru((FileKit.cacheDir / "lru_key_roulette.json").toKotlinxIoPath())
    }
    val httpClient = remember(keyRoulette) {
        createAppHttpClient().also { client ->
            // 搜索服务是全局单例，未初始化时 SearchService.httpClient 会直接抛错。
            SearchService.init(client = client, keyRoulette = keyRoulette)
        }
    }
    val providerManager = remember(httpClient, keyRoulette) { ProviderManager(httpClient, keyRoulette) }
    val ttsManager = remember(httpClient, systemTtsProvider) {
        TTSManager(httpClient = httpClient, systemProvider = systemTtsProvider)
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
        templateEngine,
        database,
        buildInfo,
        externalUriOpener,
        webServerRuntime,
        booleanPreferenceStore,
        stringPreferenceStore,
        eventBus,
        providerManager,
        analyticsTracker,
        crashReporter,
        backupFileLayout,
        oauthCallbackSessionFactory,
    ) {
        module {
            includes(appModule, dataSourceModule, repositoryModule, viewModelModule)
            single<CoroutineScope> { appScope }
            single { oauthCallbackSessionFactory }
            single<Path>(named("filesDir")) { FileKit.filesDir.toKotlinxIoPath() }
            single<Path>(named("cacheDir")) { (FileKit.cacheDir / "imggen").toKotlinxIoPath() }
            single { settingsStore }
            single { database }
            single { buildInfo }
            single { externalUriOpener }
            single<ChatInputPlatformContent> { UnavailableChatInputPlatformContent }
            single<ChatPagePlatformContent> { UnavailableChatPagePlatformContent }
            single { webServerRuntime }
            single { booleanPreferenceStore }
            single { stringPreferenceStore }
            single<ChatFontRuntime> { UnavailableChatFontRuntime }
            single { httpClient }
            single { providerManager }
            single { eventBus }
            single<AnalyticsTracker> { analyticsTracker }
            single<CrashReporter> { crashReporter }
            single { MessageFtsManager(database, MessageFtsDialect.UNICODE61) }
            single { FileKitFileCleaner(database, settingsStore) }
            single { FilesManager(get(named("filesDir")), get(), appScope, get(), asyncFileIo = true) }
            single { LocalTools(eventBus = eventBus, settingsStore = settingsStore, ttsManager = ttsManager) }
            single<DocumentTextExtractor> { UnsupportedDocumentTextExtractor }
            single { templateEngine }
            single { backupFileLayout }
        }
    }
    val koinConfiguration = remember(productModule) {
        koinConfiguration { modules(productModule) }
    }

    DisposableEffect(httpClient) {
        onDispose { httpClient.close() }
    }
    DisposableEffect(chatNotificationPresenter, appScope, eventBus, settingsStore) {
        val notificationManager = ChatNotificationManager(appScope, eventBus, settingsStore, chatNotificationPresenter)
        onDispose { notificationManager.close() }
    }

    val initialScreen = resolvedStartScreen ?: return
    val ttsState = rememberSharedCustomTtsState(
        settingsStore = settingsStore,
        ttsManager = ttsManager,
        audioPlayer = platformAudioPlayer,
    )

    KoinApplication(configuration = koinConfiguration) {
        val filesManager = koinInject<FilesManager>()
        LaunchedEffect(filesManager) { filesManager.syncFolder() }

        RikkahubTheme {
            AppRoutes(
                startScreen = initialScreen,
                ttsState = ttsState,
            )
        }
    }
}

private const val LAST_CONVERSATION_KEY = "lastConversationId"
