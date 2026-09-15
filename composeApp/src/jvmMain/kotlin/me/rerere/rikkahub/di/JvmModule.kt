package me.rerere.rikkahub.di

import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.ai.transformers.DocumentTextExtractor
import me.rerere.rikkahub.data.ai.transformers.JvmDocumentTextExtractor
import me.rerere.rikkahub.data.datastore.createJvmSettingsDataStore
import me.rerere.rikkahub.data.db.createJvmAppDatabase
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.platform.AnalyticsTracker
import me.rerere.rikkahub.platform.ChatNotificationPresenter
import me.rerere.rikkahub.platform.CrashReporter
import me.rerere.rikkahub.platform.ExternalUriOpener
import me.rerere.rikkahub.platform.JvmExternalUriOpener
import me.rerere.rikkahub.platform.JvmNucleusChatNotificationPresenter
import me.rerere.rikkahub.platform.JvmOAuthCallbackSessionFactory
import me.rerere.rikkahub.platform.JvmSentryMonitoring
import me.rerere.rikkahub.platform.OAuthCallbackSessionFactory
import me.rerere.rikkahub.shared.createAppHttpClient
import me.rerere.rikkahub.shared.currentDesktopPlatformBuildInfo
import me.rerere.rikkahub.web.WebServerRuntime
import me.rerere.rikkahub.web.createJvmWebServerRuntime
import me.rerere.search.SearchService
import me.rerere.tts.controller.JvmAudioPlayer
import me.rerere.tts.controller.MacAudioPlayer
import me.rerere.tts.controller.PlatformAudioPlayer
import me.rerere.tts.provider.TTSProvider
import me.rerere.tts.provider.TTSProviderSetting
import me.rerere.tts.provider.providers.JvmSystemTTSProvider
import org.koin.dsl.module
import org.koin.dsl.onClose

val jvmModule = module {
    single<HttpClient>(createdAtStart = true) {
        createAppHttpClient().also { SearchService.init(client = it, keyRoulette = get()) }
    } onClose { it?.close() }
    single(createdAtStart = true) {
        val appScope = get<CoroutineScope>()
        FilesManager(
            repository = get(), appScope = appScope, legacyFileCleaner = get(), asyncFileIo = true,
        ).also { manager ->
            appScope.launch { manager.syncFolder() }
        }
    }

    single<DocumentTextExtractor> { JvmDocumentTextExtractor }
    single { createJvmSettingsDataStore(get()) }
    single { createJvmAppDatabase() }
    single { currentDesktopPlatformBuildInfo() }
    single<ExternalUriOpener> { JvmExternalUriOpener() }
    single<OAuthCallbackSessionFactory> { JvmOAuthCallbackSessionFactory(get()) }
    single<WebServerRuntime> {
        createJvmWebServerRuntime(
            scope = get(),
            chatService = get(),
            conversationRepo = get(),
            folderRepo = get(),
            settingsStore = get(),
            filesManager = get(),
        )
    }
    single { JvmSentryMonitoring() }
    single<AnalyticsTracker> { get<JvmSentryMonitoring>() }
    single<CrashReporter> { get<JvmSentryMonitoring>() }
    single<ChatNotificationPresenter> { JvmNucleusChatNotificationPresenter(get()) }
    single<TTSProvider<TTSProviderSetting.SystemTTS>> { JvmSystemTTSProvider() }
    single<PlatformAudioPlayer> {
        if (System.getProperty("os.name").startsWith("Mac")) MacAudioPlayer() else JvmAudioPlayer()
    }
}
