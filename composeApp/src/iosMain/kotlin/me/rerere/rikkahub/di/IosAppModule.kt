package me.rerere.rikkahub.di

import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.CoroutineScope
import me.rerere.rikkahub.data.ai.transformers.DocumentTextExtractor
import me.rerere.rikkahub.data.ai.transformers.IosDocumentTextExtractor
import me.rerere.rikkahub.data.ai.transformers.PdfTextExtractor
import me.rerere.rikkahub.data.datastore.createIosSettingsDataStore
import me.rerere.rikkahub.data.db.createIosAppDatabase
import me.rerere.rikkahub.data.db.defaultIosDatabaseFilePath
import me.rerere.rikkahub.data.sync.BackupFileLayout
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
import me.rerere.rikkahub.shared.currentIosPlatformBuildInfo
import me.rerere.rikkahub.web.WebServerRuntime
import me.rerere.rikkahub.web.createIosWebServerRuntime
import me.rerere.tts.controller.IosAudioPlayer
import me.rerere.tts.controller.PlatformAudioPlayer
import me.rerere.tts.provider.TTSProvider
import me.rerere.tts.provider.TTSProviderSetting
import me.rerere.tts.provider.providers.IosSystemTTSProvider
import org.koin.dsl.module

fun createIosAppModule(appScope: CoroutineScope, pdfTextExtractor: PdfTextExtractor) = module {
    includes(createAppModule(appScope))
    single<PdfTextExtractor> { pdfTextExtractor }
    single<DocumentTextExtractor> { IosDocumentTextExtractor(get()) }
    single { createIosSettingsDataStore(appScope) }
    single { createIosAppDatabase() }
    single { currentIosPlatformBuildInfo() }
    single { BackupFileLayout.create(PlatformFile(defaultIosDatabaseFilePath())) }
    single<ExternalUriOpener> { IosExternalUriOpener() }
    single<OAuthCallbackSessionFactory> { IosOAuthCallbackSessionFactory() }
    single<WebServerRuntime> { createIosWebServerRuntime(appScope) }
    single<AnalyticsTracker> { IosFirebaseAnalyticsTracker() }
    single<CrashReporter> { IosFirebaseCrashReporter() }
    single<ChatNotificationPresenter> { IosUserNotificationPresenter() }
    single<TTSProvider<TTSProviderSetting.SystemTTS>> { IosSystemTTSProvider() }
    single<PlatformAudioPlayer> { IosAudioPlayer() }
}
