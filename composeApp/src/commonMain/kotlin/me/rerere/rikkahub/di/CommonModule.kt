package me.rerere.rikkahub.di

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.cacheDir
import io.github.vinceglb.filekit.div
import io.github.vinceglb.filekit.toKotlinxIoPath
import io.ktor.client.HttpClient
import korlibs.template.KorteTemplates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.Json
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.util.KeyRoulette
import me.rerere.ai.util.persistentLru
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.ai.GenerationHandler
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.ai.tools.local.LocalTools
import me.rerere.rikkahub.data.ai.tools.local.PlatformLocalTools
import me.rerere.rikkahub.data.ai.transformers.AssistantTemplateLoader
import me.rerere.rikkahub.data.ai.transformers.InputMessageTransformer
import me.rerere.rikkahub.data.ai.transformers.TemplateTransformer
import me.rerere.rikkahub.data.api.SponsorAPI
import me.rerere.rikkahub.data.datastore.BooleanPreferenceStore
import me.rerere.rikkahub.data.datastore.DataStoreBooleanPreferenceStore
import me.rerere.rikkahub.data.datastore.DataStoreStringPreferenceStore
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.StringPreferenceStore
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.databaseFile
import me.rerere.rikkahub.data.db.fts.MessageFtsDialect
import me.rerere.rikkahub.data.db.fts.MessageFtsManager
import me.rerere.rikkahub.data.event.AppEventBus
import me.rerere.rikkahub.data.files.SkillManager
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.FavoriteRepository
import me.rerere.rikkahub.data.repository.FilesRepository
import me.rerere.rikkahub.data.repository.FolderRepository
import me.rerere.rikkahub.data.repository.GenMediaRepository
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.data.sync.S3Sync
import me.rerere.rikkahub.data.sync.BackupFileLayout
import me.rerere.rikkahub.data.sync.webdav.WebDavSync
import me.rerere.rikkahub.platform.FileKitFileCleaner
import me.rerere.rikkahub.service.ChatNotificationManager
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.shared.template.createMessageTemplateEngine
import me.rerere.rikkahub.ui.pages.assistant.AssistantVM
import me.rerere.rikkahub.ui.pages.assistant.detail.AssistantDetailVM
import me.rerere.rikkahub.ui.pages.backup.BackupVM
import me.rerere.rikkahub.ui.pages.chat.ChatDrawerVM
import me.rerere.rikkahub.ui.pages.chat.ChatVM
import me.rerere.rikkahub.ui.pages.debug.DebugVM
import me.rerere.rikkahub.ui.pages.extensions.PromptVM
import me.rerere.rikkahub.ui.pages.extensions.QuickMessagesVM
import me.rerere.rikkahub.ui.pages.extensions.skills.SkillDetailVM
import me.rerere.rikkahub.ui.pages.extensions.skills.SkillsVM
import me.rerere.rikkahub.ui.pages.favorite.FavoriteVM
import me.rerere.rikkahub.ui.pages.history.HistoryVM
import me.rerere.rikkahub.ui.pages.imggen.ImgGenVM
import me.rerere.rikkahub.ui.pages.search.SearchVM
import me.rerere.rikkahub.ui.pages.setting.SettingVM
import me.rerere.rikkahub.ui.pages.share.handler.ShareHandlerVM
import me.rerere.rikkahub.ui.pages.stats.StatsVM
import me.rerere.rikkahub.ui.pages.translator.TranslatorVM
import me.rerere.rikkahub.ui.theme.ChatFontRuntime
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.UpdateChecker
import me.rerere.tts.provider.TTSManager
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.dsl.onClose

val commonModule = module {
    single { AppScope() } onClose { it?.cancel() }
    single<CoroutineScope> { get<AppScope>() }
    single { createMessageTemplateEngine() }
    single {
        SettingsStore(
            dataStore = get(),
            scope = get(),
            onSettingsChanged = get<KorteTemplates>()::invalidateCache,
        )
    }
    single<BooleanPreferenceStore> { DataStoreBooleanPreferenceStore(get()) }
    single<StringPreferenceStore> { DataStoreStringPreferenceStore(get()) }
    single { MessageFtsManager(get(), MessageFtsDialect.SIMPLE) }
    single { BackupFileLayout.create(FileKit.databaseFile) }
    single { KeyRoulette.persistentLru((FileKit.cacheDir / "lru_key_roulette.json").toKotlinxIoPath()) }
    single {
        ProviderManager(
            client = getOrNull<HttpClient>(named("ai")) ?: get(),
            keyRoulette = get(),
        )
    }
    single {
        LocalTools(
            eventBus = get(),
            settingsStore = get(),
            ttsManager = get(),
            platformTools = getOrNull<PlatformLocalTools>() ?: PlatformLocalTools.None,
        )
    }
    single { FileKitFileCleaner(get(), get()) }
    single { AppEventBus() }
    single { TTSManager(httpClient = get(), systemProvider = get()) }
    single(createdAtStart = true) {
        ChatNotificationManager(appScope = get(), eventBus = get(), settingsStore = get(), presenter = get())
    } onClose { it?.close() }

    single<Json> { JsonInstant }
    single { ChatFontRuntime() }
    single { UpdateChecker(client = get(), buildInfo = get()) }
    single {
        ChatService(
            appScope = get(),
            appEventBus = get(),
            settingsStore = get(),
            conversationRepo = get(),
            memoryRepository = get(),
            generationHandler = get(),
            templateTransformer = get(),
            providerManager = get(),
            localTools = get(),
            mcpManager = get(),
            filesManager = get(),
            skillManager = get(),
            folderRepository = get(),
            booleanPreferenceStore = get(),
            stringPreferenceStore = get(),
            workspaceReminderTransformer = getOrNull<InputMessageTransformer>(named("workspaceReminder")),
            workspaceTools = getOrNull<suspend (String?, String?) -> List<Tool>>(named("workspaceTools"))
                ?: { _, _ -> emptyList() },
        )
    }

    single<SponsorAPI> { SponsorAPI.create(get<HttpClient>()) }
    single { WebDavSync(settingsStore = get(), json = get(), layout = get(), httpClient = get()) }
    single { S3Sync(settingsStore = get(), json = get(), layout = get(), httpClient = get()) }

    single { AssistantTemplateLoader(settingsStore = get()) }
    single {
        val engine = get<KorteTemplates>()
        val loader = get<AssistantTemplateLoader>()
        engine.root = loader
        engine.includes = loader
        engine.layouts = loader
        TemplateTransformer(engine = engine)
    }

    single {
        get<AppDatabase>().conversationDao()
    }

    single {
        get<AppDatabase>().memoryDao()
    }

    single {
        get<AppDatabase>().genMediaDao()
    }

    single {
        get<AppDatabase>().messageNodeDao()
    }

    single {
        get<AppDatabase>().managedFileDao()
    }

    single {
        get<AppDatabase>().favoriteDao()
    }

    single {
        get<AppDatabase>().workspaceDao()
    }

    single {
        get<AppDatabase>().folderDao()
    }

    single {
        McpManager(
            settingsStore = get(),
            appScope = get<CoroutineScope>(),
            filesManager = get(),
            callbackSessionFactory = get(),
        )
    }
    single {
        GenerationHandler(
            providerManager = get(),
            json = get(),
            memoryRepo = get(),
        )
    }

    single { FilesRepository(get()) }

    single {
        ConversationRepository(get(), get(), get(), get(), get(), get())
    }

    single {
        FolderRepository(get(), get())
    }

    single {
        MemoryRepository(get())
    }

    single {
        GenMediaRepository(get())
    }

    single {
        FavoriteRepository(get())
    }

    single {
        SkillManager(settingsStore = get())
    }

    viewModel<ShareHandlerVM> {
        ShareHandlerVM(
            text = it.get(),
            settingsStore = get(),
        )
    }
    viewModel<ChatVM> { params ->
        ChatVM(
            id = params.get(),
            settingsStore = get(),
            conversationRepo = get(),
            chatService = get(),
            updateChecker = get(),
            analytics = get(),
            favoriteRepository = get(),
        )
    }
    viewModelOf(::ChatDrawerVM)
    viewModelOf(::DebugVM)
    viewModel<SettingVM> {
        get<McpManager>()
        SettingVM(settingsStore = get())
    }

    viewModelOf(::HistoryVM)
    viewModelOf(::AssistantVM)
    viewModel<AssistantDetailVM> {
        AssistantDetailVM(
            id = it.get(),
            settingsStore = get(),
            memoryRepository = get(),
            filesManager = get(),
            skillManager = get(),
            workspaceDao = get(),
        )
    }
    viewModel { TranslatorVM(get(), get()) }

    viewModel { BackupVM(get(), get(), get(), get()) }
    viewModel {
        ImgGenVM(get(), get(), get())
    }
    viewModelOf(::PromptVM)
    viewModelOf(::QuickMessagesVM)
    viewModelOf(::SkillsVM)
    viewModelOf(::SkillDetailVM)

    viewModelOf(::FavoriteVM)
    viewModelOf(::SearchVM)
    viewModel { StatsVM(get(), get(), get()) }
}
