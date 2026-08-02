package me.rerere.rikkahub.shared

import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import me.rerere.ai.provider.ProviderManager
import me.rerere.rikkahub.data.ai.mcp.McpRuntime
import me.rerere.rikkahub.data.api.SponsorAPI
import me.rerere.rikkahub.data.datastore.BooleanPreferenceStore
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.StringPreferenceStore
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.dao.ConversationDAO
import me.rerere.rikkahub.data.db.fts.MessageFtsDialect
import me.rerere.rikkahub.data.db.fts.MessageFtsManager
import me.rerere.rikkahub.data.files.SkillStore
import me.rerere.rikkahub.data.repository.BackupLocalFileService
import me.rerere.rikkahub.data.repository.BackupRepository
import me.rerere.rikkahub.data.repository.BackupSettingsGateway
import me.rerere.rikkahub.data.repository.ConversationFileStore
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.FavoriteRepository
import me.rerere.rikkahub.data.repository.FolderRepository
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.data.repository.MessageNodeReadErrorPolicy
import me.rerere.rikkahub.data.repository.RoomStatsQueries
import me.rerere.rikkahub.data.repository.SettingsStoreBackupSettingsGateway
import me.rerere.rikkahub.data.repository.StatsQueries
import me.rerere.rikkahub.data.repository.StatsRepository
import me.rerere.rikkahub.data.sync.S3BackupTransport
import me.rerere.rikkahub.data.sync.WebDavBackupTransport
import me.rerere.rikkahub.data.event.AppEventBus
import me.rerere.rikkahub.platform.AnalyticsTracker
import me.rerere.rikkahub.platform.CrashReporter
import me.rerere.rikkahub.platform.ExternalUriOpener
import me.rerere.rikkahub.service.ChatRuntime
import me.rerere.rikkahub.service.ImageGenerationRuntime
import me.rerere.rikkahub.service.SharedChatAttachmentStore
import me.rerere.rikkahub.service.SharedChatRuntime
import me.rerere.rikkahub.service.SharedImageGenerationRuntime
import me.rerere.rikkahub.service.SharedTranslationRuntime
import me.rerere.rikkahub.service.TranslationRuntime
import me.rerere.rikkahub.ui.pages.assistant.AssistantAssetCleaner
import me.rerere.rikkahub.ui.pages.assistant.AssistantSkillCatalog
import me.rerere.rikkahub.ui.pages.assistant.AssistantSkillMetadata
import me.rerere.rikkahub.ui.pages.assistant.detail.AssistantPromptPreviewRuntime
import me.rerere.rikkahub.ui.pages.assistant.detail.CommonAssistantPromptPreviewRuntime
import me.rerere.rikkahub.ui.components.message.ChatMessagePlatformActions
import me.rerere.rikkahub.ui.components.message.SharedChatMessagePlatformActions
import me.rerere.rikkahub.ui.components.ai.ChatInputPlatformContent
import me.rerere.rikkahub.ui.components.ai.SharedChatInputPlatformContent
import me.rerere.rikkahub.ui.pages.chat.ChatPagePlatformContent
import me.rerere.rikkahub.ui.pages.chat.SharedChatPagePlatformContent
import me.rerere.rikkahub.ui.pages.chat.ChatDrawerVM
import me.rerere.rikkahub.ui.pages.chat.ChatVM
import me.rerere.rikkahub.ui.pages.assistant.AssistantVM
import me.rerere.rikkahub.ui.pages.assistant.detail.AssistantDetailVM
import me.rerere.rikkahub.ui.pages.backup.BackupVM
import me.rerere.rikkahub.ui.pages.extensions.PromptVM
import me.rerere.rikkahub.ui.pages.extensions.QuickMessagesVM
import me.rerere.rikkahub.ui.pages.extensions.skills.SkillDetailVM
import me.rerere.rikkahub.ui.pages.extensions.skills.SkillsVM
import me.rerere.rikkahub.ui.pages.favorite.FavoriteVM
import me.rerere.rikkahub.ui.pages.history.HistoryVM
import me.rerere.rikkahub.ui.pages.imggen.ImgGenVM
import me.rerere.rikkahub.ui.pages.search.SearchVM
import me.rerere.rikkahub.ui.pages.setting.ChatStorageSummaryProvider
import me.rerere.rikkahub.ui.pages.setting.SettingVM
import me.rerere.rikkahub.ui.pages.stats.StatsVM
import me.rerere.rikkahub.ui.pages.translator.TranslatorVM
import me.rerere.rikkahub.ui.theme.ChatFontRuntime
import me.rerere.rikkahub.utils.UpdateChecker
import me.rerere.rikkahub.web.WebServerRuntime
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

internal fun sharedProductModule(
    settingsStore: SettingsStore,
    database: AppDatabase,
    buildInfo: PlatformBuildInfo,
    externalUriOpener: ExternalUriOpener,
    webServerRuntime: WebServerRuntime,
    booleanPreferenceStore: BooleanPreferenceStore,
    stringPreferenceStore: StringPreferenceStore,
    chatFontRuntime: ChatFontRuntime,
    chatStorageSummaryProvider: ChatStorageSummaryProvider,
    httpClient: HttpClient,
    providerManager: ProviderManager,
    appScope: CoroutineScope,
    analyticsTracker: AnalyticsTracker,
    crashReporter: CrashReporter,
    eventBus: AppEventBus,
): Module = module {
    single { settingsStore }
    single { database }
    single { buildInfo }
    single { externalUriOpener }
    single { SharedChatAttachmentStore() }
    single<ChatMessagePlatformActions> { SharedChatMessagePlatformActions(externalUriOpener) }
    single<ChatInputPlatformContent> { SharedChatInputPlatformContent(get()) }
    single<ChatPagePlatformContent> { SharedChatPagePlatformContent(get()) }
    single { webServerRuntime }
    single { booleanPreferenceStore }
    single { stringPreferenceStore }
    single { chatFontRuntime }
    single { chatStorageSummaryProvider }
    single { httpClient }
    single { providerManager }
    single { eventBus }
    single<AnalyticsTracker> { analyticsTracker }
    single<CrashReporter> { crashReporter }
    single { UpdateChecker(client = httpClient, buildInfo = buildInfo) }
    single<SponsorAPI> { SponsorAPI.create(httpClient) }

    single { database.conversationDao() }
    single { database.memoryDao() }
    single { database.genMediaDao() }
    single { database.messageNodeDao() }
    single { database.favoriteDao() }
    single { database.workspaceDao() }
    single { database.folderDao() }
    single { MessageFtsManager(database, MessageFtsDialect.UNICODE61) }
    single<ConversationFileStore> { ConversationFileStore { } }
    single<MessageNodeReadErrorPolicy> { MessageNodeReadErrorPolicy.Default }
    single {
        ConversationRepository(
            conversationDAO = get(),
            messageNodeDAO = get(),
            favoriteDAO = get(),
            database = database,
            conversationFileStore = get(),
            messageFtsManager = get(),
            messageNodeReadErrorPolicy = get(),
        )
    }
    single {
        val conversationDao: ConversationDAO = get()
        FolderRepository(folderDAO = get(), clearConversationFolder = conversationDao::clearFolder)
    }
    single<ChatRuntime> {
        SharedChatRuntime(
            scope = appScope,
            settingsStore = settingsStore,
            conversationRepository = get(),
            folderRepository = get(),
            providerManager = providerManager,
            eventBus = eventBus,
            booleanPreferenceStore = booleanPreferenceStore,
            stringPreferenceStore = stringPreferenceStore,
            attachmentStore = get(),
        )
    }
    single { MemoryRepository(get()) }
    single { FavoriteRepository(get()) }
    single<StatsQueries> { RoomStatsQueries(get(), get()) }
    single {
        StatsRepository(
            queries = get(),
            launchCountProvider = { settingsStore.settingsFlow.value.launchCount },
        )
    }

    single<SkillStore> { EmptySkillStore }
    single<AssistantAssetCleaner> { AssistantAssetCleaner { } }
    single<AssistantSkillCatalog> {
        AssistantSkillCatalog {
            get<SkillStore>().listSkills().map { skill ->
                AssistantSkillMetadata(
                    key = skill.name,
                    name = skill.name,
                    description = skill.description,
                )
            }
        }
    }
    single<AssistantPromptPreviewRuntime> { CommonAssistantPromptPreviewRuntime }
    single<McpRuntime> { UnavailableMcpRuntime }
    single<TranslationRuntime> { SharedTranslationRuntime(settingsStore, providerManager) }
    single<ImageGenerationRuntime> { SharedImageGenerationRuntime(settingsStore, providerManager, get()) }
    single<BackupSettingsGateway> { SettingsStoreBackupSettingsGateway(settingsStore) }
    single<WebDavBackupTransport> { UnavailableWebDavBackupTransport }
    single<S3BackupTransport> { UnavailableS3BackupTransport }
    single { BackupRepository(get(), get(), get()) }
    single<BackupLocalFileService> { UnavailableBackupLocalFileService }

    viewModelOf(::SettingVM)
    viewModelOf(::SearchVM)
    viewModelOf(::HistoryVM)
    viewModelOf(::FavoriteVM)
    viewModelOf(::StatsVM)
    viewModelOf(::AssistantVM)
    viewModel<AssistantDetailVM> { parameters ->
        AssistantDetailVM(
            id = parameters.get(),
            settingsStore = get(),
            memoryRepository = get(),
            assetCleaner = get(),
            skillCatalog = get(),
            workspaceDao = get(),
        )
    }
    viewModelOf(::PromptVM)
    viewModelOf(::QuickMessagesVM)
    viewModelOf(::SkillsVM)
    viewModelOf(::SkillDetailVM)
    viewModelOf(::BackupVM)
    viewModelOf(::ImgGenVM)
    viewModelOf(::TranslatorVM)
    viewModel<ChatVM> { parameters ->
        ChatVM(
            id = parameters.get(),
            settingsStore = get(),
            conversationRepo = get(),
            chatService = get(),
            updateChecker = get(),
            analytics = get(),
            favoriteRepository = get(),
        )
    }
    viewModelOf(::ChatDrawerVM)
}
