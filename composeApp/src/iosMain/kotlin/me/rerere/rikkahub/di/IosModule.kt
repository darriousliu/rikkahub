package me.rerere.rikkahub.di

import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.ai.transformers.DocumentTextExtractor
import me.rerere.rikkahub.data.ai.transformers.IosDocumentTextExtractor
import me.rerere.rikkahub.data.ai.transformers.PdfTextExtractor
import me.rerere.rikkahub.data.datastore.createIosSettingsDataStore
import me.rerere.rikkahub.data.db.createIosAppDatabase
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.platform.AnalyticsTracker
import me.rerere.rikkahub.platform.ChatNotificationPresenter
import me.rerere.rikkahub.platform.CrashReporter
import me.rerere.rikkahub.platform.ExternalUriOpener
import me.rerere.rikkahub.platform.IosExternalUriOpener
import me.rerere.rikkahub.platform.IosFirebaseAnalyticsTracker
import me.rerere.rikkahub.platform.IosFirebaseCrashReporter
import me.rerere.rikkahub.platform.IosOAuthCallbackSessionFactory
import me.rerere.rikkahub.platform.IosUserNotificationPresenter
import me.rerere.rikkahub.platform.OAuthCallbackSessionFactory
import me.rerere.rikkahub.shared.createAppHttpClient
import me.rerere.rikkahub.shared.currentIosPlatformBuildInfo
import me.rerere.rikkahub.web.WebServerRuntime
import me.rerere.rikkahub.web.createIosWebServerRuntime
import me.rerere.search.SearchService
import me.rerere.tts.controller.IosAudioPlayer
import me.rerere.tts.controller.PlatformAudioPlayer
import me.rerere.tts.provider.TTSProvider
import me.rerere.tts.provider.TTSProviderSetting
import me.rerere.tts.provider.providers.IosSystemTTSProvider
import org.koin.dsl.module
import org.koin.dsl.onClose

fun iosModule(pdfTextExtractor: PdfTextExtractor) = module {
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

    single<PdfTextExtractor> { pdfTextExtractor }
    single<DocumentTextExtractor> { IosDocumentTextExtractor(get()) }
    single { createIosSettingsDataStore(get()) }
    single { createIosAppDatabase() }
    single { currentIosPlatformBuildInfo() }
    single<ExternalUriOpener> { IosExternalUriOpener() }
    single<OAuthCallbackSessionFactory> { IosOAuthCallbackSessionFactory() }
    single<WebServerRuntime> { createIosWebServerRuntime(get()) }
    single<AnalyticsTracker> { IosFirebaseAnalyticsTracker() }
    single<CrashReporter> { IosFirebaseCrashReporter() }
    single<ChatNotificationPresenter> { IosUserNotificationPresenter() }
    single<TTSProvider<TTSProviderSetting.SystemTTS>> { IosSystemTTSProvider() }
    single<PlatformAudioPlayer> { IosAudioPlayer() }
}
