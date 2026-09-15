package me.rerere.rikkahub.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import io.github.vinceglb.filekit.FileKit
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
import me.rerere.rikkahub.data.datastore.BooleanPreferenceStore
import me.rerere.rikkahub.data.datastore.DataStoreBooleanPreferenceStore
import me.rerere.rikkahub.data.datastore.DataStoreStringPreferenceStore
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.StringPreferenceStore
import me.rerere.rikkahub.data.db.fts.MessageFtsDialect
import me.rerere.rikkahub.data.db.fts.MessageFtsManager
import me.rerere.rikkahub.data.event.AppEventBus
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.platform.FileKitFileCleaner
import me.rerere.rikkahub.service.ChatNotificationManager
import me.rerere.rikkahub.shared.createAppHttpClient
import me.rerere.rikkahub.shared.template.createMessageTemplateEngine
import me.rerere.search.SearchService
import me.rerere.tts.provider.TTSManager
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.dsl.onClose

/** iOS 和桌面的应用依赖；平台模块提供数据库、DataStore 和系统服务。 */
fun createAppModule(appScope: CoroutineScope): Module = module {
    includes(appModule, dataSourceModule, repositoryModule, viewModelModule)
    single<CoroutineScope> { appScope }
    single { createMessageTemplateEngine() }
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
    single { TTSManager(httpClient = get(), systemProvider = get()) }
    single { AppEventBus() }
    single { MessageFtsManager(get(), MessageFtsDialect.UNICODE61) }
    single { FileKitFileCleaner(get(), get()) }
    single(createdAtStart = true) {
        FilesManager(
            repository = get(), appScope = appScope, legacyFileCleaner = get(), asyncFileIo = true,
        ).also { manager ->
            appScope.launch { manager.syncFolder() }
        }
    }
    single { LocalTools(eventBus = get(), settingsStore = get(), ttsManager = get()) }
    single(createdAtStart = true) {
        ChatNotificationManager(appScope, get(), get(), get())
    } onClose { it?.close() }
}
