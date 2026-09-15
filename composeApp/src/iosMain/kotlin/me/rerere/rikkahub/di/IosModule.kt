package me.rerere.rikkahub.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.cacheDir
import io.github.vinceglb.filekit.div
import io.github.vinceglb.filekit.toKotlinxIoPath
import io.ktor.client.HttpClient
import korlibs.template.KorteTemplates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.util.KeyRoulette
import me.rerere.ai.util.persistentLru
import me.rerere.rikkahub.data.ai.tools.local.LocalTools
import me.rerere.rikkahub.data.ai.transformers.DocumentTextExtractor
import me.rerere.rikkahub.data.ai.transformers.IosDocumentTextExtractor
import me.rerere.rikkahub.data.ai.transformers.PdfTextExtractor
import me.rerere.rikkahub.data.datastore.BooleanPreferenceStore
import me.rerere.rikkahub.data.datastore.DataStoreBooleanPreferenceStore
import me.rerere.rikkahub.data.datastore.DataStoreStringPreferenceStore
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.StringPreferenceStore
import me.rerere.rikkahub.data.datastore.createIosSettingsDataStore
import me.rerere.rikkahub.data.db.createIosAppDatabase
import me.rerere.rikkahub.data.db.defaultIosDatabaseFilePath
import me.rerere.rikkahub.data.db.fts.MessageFtsDialect
import me.rerere.rikkahub.data.db.fts.MessageFtsManager
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.sync.BackupFileLayout
import me.rerere.rikkahub.platform.AnalyticsTracker
import me.rerere.rikkahub.platform.ChatNotificationPresenter
import me.rerere.rikkahub.platform.CrashReporter
import me.rerere.rikkahub.platform.ExternalUriOpener
import me.rerere.rikkahub.platform.FileKitFileCleaner
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
    single {
        SettingsStore(
            dataStore = get<DataStore<Preferences>>(),
            scope = get(),
            onSettingsChanged = get<KorteTemplates>()::invalidateCache,
        )
    }
    single<BooleanPreferenceStore> { DataStoreBooleanPreferenceStore(get()) }
    single<StringPreferenceStore> { DataStoreStringPreferenceStore(get()) }
    single { KeyRoulette.persistentLru((FileKit.cacheDir / "lru_key_roulette.json").toKotlinxIoPath()) }
    single<HttpClient>(createdAtStart = true) {
        createAppHttpClient().also { SearchService.init(client = it, keyRoulette = get()) }
    } onClose { it?.close() }
    single { ProviderManager(get(), get()) }
    single { MessageFtsManager(get(), MessageFtsDialect.SIMPLE) }
    single { FileKitFileCleaner(get(), get()) }
    single(createdAtStart = true) {
        val appScope = get<CoroutineScope>()
        FilesManager(
            repository = get(), appScope = appScope, legacyFileCleaner = get(), asyncFileIo = true,
        ).also { manager ->
            appScope.launch { manager.syncFolder() }
        }
    }
    single { LocalTools(eventBus = get(), settingsStore = get(), ttsManager = get()) }

    single<PdfTextExtractor> { pdfTextExtractor }
    single<DocumentTextExtractor> { IosDocumentTextExtractor(get()) }
    single { createIosSettingsDataStore(get()) }
    single { createIosAppDatabase() }
    single { currentIosPlatformBuildInfo() }
    single { BackupFileLayout.create(PlatformFile(defaultIosDatabaseFilePath())) }
    single<ExternalUriOpener> { IosExternalUriOpener() }
    single<OAuthCallbackSessionFactory> { IosOAuthCallbackSessionFactory() }
    single<WebServerRuntime> { createIosWebServerRuntime(get()) }
    single<AnalyticsTracker> { IosFirebaseAnalyticsTracker() }
    single<CrashReporter> { IosFirebaseCrashReporter() }
    single<ChatNotificationPresenter> { IosUserNotificationPresenter() }
    single<TTSProvider<TTSProviderSetting.SystemTTS>> { IosSystemTTSProvider() }
    single<PlatformAudioPlayer> { IosAudioPlayer() }
}
