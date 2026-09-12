package me.rerere.rikkahub.shared

import io.github.vinceglb.filekit.div
import io.github.vinceglb.filekit.cacheDir
import io.ktor.client.HttpClient
import korlibs.template.KorteTemplates
import kotlinx.coroutines.CoroutineScope
import kotlinx.io.files.Path
import me.rerere.ai.provider.ProviderManager
import me.rerere.rikkahub.data.ai.mcp.FileKitMcpImageStore
import me.rerere.rikkahub.data.ai.mcp.McpImageStore
import me.rerere.rikkahub.data.ai.tools.local.LocalTools
import me.rerere.rikkahub.data.ai.transformers.Base64ImageStore
import me.rerere.rikkahub.data.ai.transformers.DocumentTextExtractor
import me.rerere.rikkahub.data.ai.transformers.SharedBase64ImageStore
import me.rerere.rikkahub.data.ai.transformers.UnsupportedDocumentTextExtractor
import me.rerere.rikkahub.data.api.SponsorAPI
import me.rerere.rikkahub.data.datastore.BooleanPreferenceStore
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.StringPreferenceStore
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.fts.MessageFtsDialect
import me.rerere.rikkahub.data.db.fts.MessageFtsManager
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.filesDir
import io.github.vinceglb.filekit.toKotlinxIoPath
import me.rerere.rikkahub.data.files.ChatFileStore
import me.rerere.rikkahub.data.repository.ConversationFileStore
import me.rerere.rikkahub.data.repository.MessageNodeReadErrorPolicy
import me.rerere.rikkahub.data.sync.S3Sync
import me.rerere.rikkahub.data.sync.webdav.WebDavSync
import me.rerere.rikkahub.data.sync.BackupFileLayout
import me.rerere.rikkahub.data.event.AppEventBus
import me.rerere.rikkahub.platform.AnalyticsTracker
import me.rerere.rikkahub.platform.CrashReporter
import me.rerere.rikkahub.platform.ExternalUriOpener
import me.rerere.rikkahub.platform.OAuthCallbackSessionFactory
import me.rerere.rikkahub.di.appModule
import me.rerere.rikkahub.di.dataSourceModule
import me.rerere.rikkahub.di.repositoryModule
import me.rerere.rikkahub.di.viewModelModule
import me.rerere.rikkahub.service.FileKitChatFileStore
import me.rerere.rikkahub.service.SharedChatAttachmentStore
import me.rerere.rikkahub.ui.pages.assistant.AssistantAssetCleaner
import me.rerere.rikkahub.ui.components.message.ChatMessagePlatformActions
import me.rerere.rikkahub.ui.components.ai.ChatInputPlatformContent
import me.rerere.rikkahub.ui.components.ai.SharedChatInputPlatformContent
import me.rerere.rikkahub.ui.pages.chat.ChatPagePlatformContent
import me.rerere.rikkahub.ui.pages.chat.SharedChatPagePlatformContent
import me.rerere.rikkahub.ui.pages.setting.ChatStorageSummaryProvider
import me.rerere.rikkahub.ui.theme.ChatFontRuntime
import me.rerere.rikkahub.utils.UpdateChecker
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.web.WebServerRuntime
import me.rerere.tts.provider.TTSManager
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import me.rerere.rikkahub.platform.FileKitFileCleaner

internal fun platformModule(
    settingsStore: SettingsStore,
    templateEngine: KorteTemplates,
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
    chatMessagePlatformActions: ChatMessagePlatformActions,
    backupFileLayout: BackupFileLayout,
    oauthCallbackSessionFactory: OAuthCallbackSessionFactory,
    ttsManager: TTSManager?,
): Module = module {
    includes(appModule, dataSourceModule, repositoryModule, viewModelModule)
    single<CoroutineScope> { appScope }
    single { oauthCallbackSessionFactory }
    single<Path>(named("filesDir")) { FileKit.filesDir.toKotlinxIoPath() }
    single<Path>(named("cacheDir")) { (FileKit.cacheDir / "imggen").toKotlinxIoPath() }
    single<ChatFileStore> { FileKitChatFileStore(appScope, get()) }
    single { settingsStore }
    single { database }
    single { buildInfo }
    single { externalUriOpener }
    single { SharedChatAttachmentStore() }
    single<ChatMessagePlatformActions> { chatMessagePlatformActions }
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
    single<McpImageStore> { FileKitMcpImageStore() }
    single<AnalyticsTracker> { analyticsTracker }
    single<CrashReporter> { crashReporter }
    single { UpdateChecker(client = httpClient, buildInfo = buildInfo) }
    single<SponsorAPI> { SponsorAPI.create(httpClient) }

    single { MessageFtsManager(database, MessageFtsDialect.UNICODE61) }
    single { FileKitFileCleaner(database, settingsStore) }
    single<ConversationFileStore> { get<FileKitFileCleaner>() }
    single<MessageNodeReadErrorPolicy> { MessageNodeReadErrorPolicy.Default }
    single<Base64ImageStore> { SharedBase64ImageStore() }
    single { LocalTools(eventBus = eventBus, settingsStore = settingsStore, ttsManager = ttsManager) }
    single<DocumentTextExtractor> { UnsupportedDocumentTextExtractor }
    single { templateEngine }
    single<AssistantAssetCleaner> { get<FileKitFileCleaner>() }
    single { WebDavSync(get(), JsonInstant, backupFileLayout, httpClient) }
    single { S3Sync(get(), JsonInstant, backupFileLayout, httpClient) }

}
