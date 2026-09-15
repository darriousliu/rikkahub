package me.rerere.rikkahub

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.dokar.sonner.Toaster
import com.dokar.sonner.rememberToasterState
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.first
import me.rerere.rikkahub.data.datastore.BooleanPreferenceStore
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.StringPreferenceStore
import me.rerere.rikkahub.data.db.DatabaseMigrationTracker
import me.rerere.rikkahub.data.db.MigrationState
import me.rerere.rikkahub.data.event.AppEvent
import me.rerere.rikkahub.data.event.AppEventBus
import me.rerere.rikkahub.generated.resources.Res
import me.rerere.rikkahub.generated.resources.db_migrating
import me.rerere.rikkahub.shared.PlatformBuildInfo
import me.rerere.rikkahub.shared.currentPlatformKind
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.TTSController
import me.rerere.rikkahub.ui.context.LocalASRState
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalSharedTransitionScope
import me.rerere.rikkahub.ui.context.LocalTTSState
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.context.Navigator
import me.rerere.rikkahub.ui.hooks.rememberCustomAsrState
import me.rerere.rikkahub.ui.hooks.rememberCustomTtsState
import me.rerere.rikkahub.ui.pages.assistant.AssistantPage
import me.rerere.rikkahub.ui.pages.assistant.detail.AssistantBasicPage
import me.rerere.rikkahub.ui.pages.assistant.detail.AssistantDetailPage
import me.rerere.rikkahub.ui.pages.assistant.detail.AssistantExtensionsPage
import me.rerere.rikkahub.ui.pages.assistant.detail.AssistantLocalToolPage
import me.rerere.rikkahub.ui.pages.assistant.detail.AssistantMcpPage
import me.rerere.rikkahub.ui.pages.assistant.detail.AssistantMemoryPage
import me.rerere.rikkahub.ui.pages.assistant.detail.AssistantPromptPage
import me.rerere.rikkahub.ui.pages.assistant.detail.AssistantRequestPage
import me.rerere.rikkahub.ui.pages.backup.BackupPage
import me.rerere.rikkahub.ui.pages.chat.ChatPage
import me.rerere.rikkahub.ui.pages.debug.DebugPage
import me.rerere.rikkahub.ui.pages.extensions.ExtensionsPage
import me.rerere.rikkahub.ui.pages.extensions.PromptPage
import me.rerere.rikkahub.ui.pages.extensions.QuickMessagesPage
import me.rerere.rikkahub.ui.pages.extensions.skills.SkillDetailPage
import me.rerere.rikkahub.ui.pages.extensions.skills.SkillsPage
import me.rerere.rikkahub.ui.pages.extensions.workspace.WorkspaceDetailPage
import me.rerere.rikkahub.ui.pages.extensions.workspace.WorkspaceFileEditorPage
import me.rerere.rikkahub.ui.pages.extensions.workspace.WorkspacePage
import me.rerere.rikkahub.ui.pages.extensions.workspace.WorkspaceTerminalPage
import me.rerere.rikkahub.ui.pages.favorite.FavoritePage
import me.rerere.rikkahub.ui.pages.history.HistoryPage
import me.rerere.rikkahub.ui.pages.imggen.ImageGenPage
import me.rerere.rikkahub.ui.pages.log.LogPage
import me.rerere.rikkahub.ui.pages.search.SearchPage
import me.rerere.rikkahub.ui.pages.setting.SettingAboutPage
import me.rerere.rikkahub.ui.pages.setting.SettingDonatePage
import me.rerere.rikkahub.ui.pages.setting.SettingFilesPage
import me.rerere.rikkahub.ui.pages.setting.SettingMcpPage
import me.rerere.rikkahub.ui.pages.setting.SettingModelPage
import me.rerere.rikkahub.ui.pages.setting.SettingPage
import me.rerere.rikkahub.ui.pages.setting.SettingPreferencesGeneralPage
import me.rerere.rikkahub.ui.pages.setting.SettingPreferencesNotificationPage
import me.rerere.rikkahub.ui.pages.setting.SettingPreferencesPage
import me.rerere.rikkahub.ui.pages.setting.SettingPreferencesThemePage
import me.rerere.rikkahub.ui.pages.setting.SettingPreferencesUIPage
import me.rerere.rikkahub.ui.pages.setting.SettingProviderDetailPage
import me.rerere.rikkahub.ui.pages.setting.SettingProviderPage
import me.rerere.rikkahub.ui.pages.setting.SettingSearchDetailPage
import me.rerere.rikkahub.ui.pages.setting.SettingSearchPage
import me.rerere.rikkahub.ui.pages.setting.SettingSpeechPage
import me.rerere.rikkahub.ui.pages.setting.SettingThemePage
import me.rerere.rikkahub.ui.pages.setting.SettingWebPage
import me.rerere.rikkahub.ui.pages.share.handler.ShareHandlerPage
import me.rerere.rikkahub.ui.pages.stats.StatsPage
import me.rerere.rikkahub.ui.pages.translator.TranslatorPage
import me.rerere.rikkahub.ui.pages.webview.WebViewPage
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
fun AppRoutes(
    startScreen: Screen? = null,
    modifier: Modifier = Modifier,
    onOpenUsageAccessSettings: () -> Unit = {},
    onBackStackChanged: (MutableList<NavKey>) -> Unit = {},
) {
    val toastState = rememberToasterState()
    val ttsState = rememberCustomTtsState()
    val asrState = rememberCustomAsrState()
    val stringPreferenceStore = koinInject<StringPreferenceStore>()
    val booleanPreferenceStore = koinInject<BooleanPreferenceStore>()
    val resolvedStartScreen by produceState<Screen?>(
        startScreen, startScreen, stringPreferenceStore, booleanPreferenceStore,
    ) {
        if (value == null) {
            val createNew = booleanPreferenceStore.observe("create_new_conversation_on_start", true).first()
            val rememberedId = if (createNew) null else stringPreferenceStore.get("lastConversationId")
                ?.let { stored -> runCatching { Uuid.parse(stored) }.getOrNull() }
            value = Screen.Chat((rememberedId ?: Uuid.random()).toString())
        }
    }
    val initialScreen = resolvedStartScreen ?: return
    val settingsStore = koinInject<SettingsStore>()
    val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
    val eventBus = koinInject<AppEventBus>()
    val buildInfo = koinInject<PlatformBuildInfo>()
    val migrationState by DatabaseMigrationTracker.state.collectAsStateWithLifecycle()
    val backStack = rememberNavBackStack(navigationSavedStateConfiguration, initialScreen)
    val navigator = remember(backStack) { Navigator(backStack) }

    SideEffect { onBackStackChanged(backStack) }
    LaunchedEffect(ttsState, onOpenUsageAccessSettings) {
        eventBus.events.collect { event ->
            when (event) {
                is AppEvent.Speak -> ttsState.speak(event.text)
                is AppEvent.OpenUsageAccessSettings -> onOpenUsageAccessSettings()
                is AppEvent.McpOAuthCallback,
                is AppEvent.ChatGenerationUpdate,
                is AppEvent.ChatGenerationEnded -> Unit
            }
        }
    }

    SharedTransitionLayout(modifier = modifier) {
        CompositionLocalProvider(
            LocalNavController provides navigator,
            LocalSharedTransitionScope provides this,
            LocalSettings provides settings,
            LocalToaster provides toastState,
            LocalTTSState provides ttsState,
            LocalASRState provides asrState,
        ) {
            Toaster(
                state = toastState,
                darkTheme = LocalDarkMode.current,
                richColors = true,
                alignment = Alignment.TopCenter,
                showCloseButton = true,
            )
            TTSController()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            ) {
                NavDisplay(
                    backStack = backStack,
                    entryDecorators = listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                    modifier = Modifier.fillMaxSize(),
                    onBack = { backStack.removeLastOrNull() },
                    transitionSpec = {
                        if (backStack.size == 1) {
                            fadeIn() togetherWith fadeOut()
                        } else {
                            slideInHorizontally { it } togetherWith
                                slideOutHorizontally { -it / 2 } + scaleOut(targetScale = 0.7f) + fadeOut()
                        }
                    },
                    popTransitionSpec = {
                        slideInHorizontally { -it / 2 } + scaleIn(initialScale = 0.7f) + fadeIn() togetherWith
                            slideOutHorizontally { it }
                    },
                    predictivePopTransitionSpec = {
                        slideInHorizontally { -it / 2 } + scaleIn(initialScale = 0.7f) + fadeIn() togetherWith
                            slideOutHorizontally { it }
                    },
                    entryProvider = entryProvider(
                        fallback = { key -> NavEntry(key) { UnavailableRoute(it) } },
                    ) {
                        entry<Screen.Chat>(
                            metadata = NavDisplay.transitionSpec { fadeIn() togetherWith fadeOut() }
                                + NavDisplay.popTransitionSpec { fadeIn() togetherWith fadeOut() },
                        ) { key ->
                            ChatPage(
                                id = Uuid.parse(key.id),
                                text = key.text,
                                files = key.files,
                                nodeId = key.nodeId?.let(Uuid::parse),
                            )
                        }
                        entry<Screen.ShareHandler> { ShareHandlerPage(it.text, it.streamUri) }
                        entry<Screen.History> { HistoryPage() }
                        entry<Screen.Favorite> { FavoritePage() }
                        entry<Screen.Assistant> { AssistantPage() }
                        entry<Screen.AssistantDetail> { AssistantDetailPage(it.id) }
                        entry<Screen.AssistantBasic> { AssistantBasicPage(it.id) }
                        entry<Screen.AssistantPrompt> { AssistantPromptPage(it.id) }
                        entry<Screen.AssistantMemory> { AssistantMemoryPage(it.id) }
                        entry<Screen.AssistantRequest> { AssistantRequestPage(it.id) }
                        entry<Screen.AssistantMcp> { AssistantMcpPage(it.id) }
                        entry<Screen.AssistantLocalTool> { AssistantLocalToolPage(it.id) }
                        entry<Screen.AssistantInjections> { AssistantExtensionsPage(it.id) }
                        entry<Screen.Translator> { TranslatorPage() }
                        entry<Screen.Setting> { SettingPage() }
                        entry<Screen.Backup> { BackupPage() }
                        entry<Screen.ImageGen> { ImageGenPage() }
                        entry<Screen.WebView> { WebViewPage(it.url, it.contentId) }
                        entry<Screen.SettingTheme> { SettingThemePage() }
                        entry<Screen.SettingPreferences> { SettingPreferencesPage() }
                        entry<Screen.SettingPreferencesTheme> { SettingPreferencesThemePage() }
                        entry<Screen.SettingPreferencesNotification> { SettingPreferencesNotificationPage() }
                        entry<Screen.SettingPreferencesGeneral> { SettingPreferencesGeneralPage() }
                        entry<Screen.SettingPreferencesUI> { SettingPreferencesUIPage() }
                        entry<Screen.SettingProvider> { SettingProviderPage() }
                        entry<Screen.SettingProviderDetail> { SettingProviderDetailPage(Uuid.parse(it.providerId)) }
                        entry<Screen.SettingModels> { SettingModelPage() }
                        entry<Screen.SettingAbout> { SettingAboutPage() }
                        entry<Screen.SettingSearch> { SettingSearchPage() }
                        entry<Screen.SettingSearchDetail> { SettingSearchDetailPage(Uuid.parse(it.serviceId)) }
                        entry<Screen.SettingSpeech> { SettingSpeechPage() }
                        entry<Screen.SettingMcp> { SettingMcpPage() }
                        entry<Screen.SettingDonate> { SettingDonatePage() }
                        entry<Screen.SettingFiles> { SettingFilesPage() }
                        entry<Screen.SettingWeb> { SettingWebPage() }
                        entry<Screen.Log> { LogPage() }
                        entry<Screen.Debug> { DebugPage() }
                        entry<Screen.Extensions> { ExtensionsPage() }
                        entry<Screen.QuickMessages> { QuickMessagesPage() }
                        entry<Screen.Prompts> { PromptPage() }
                        entry<Screen.Skills> { SkillsPage() }
                        entry<Screen.Workspaces> { WorkspacePage() }
                        entry<Screen.WorkspaceDetail> { WorkspaceDetailPage(it.id) }
                        entry<Screen.WorkspaceTerminal> { WorkspaceTerminalPage(it.id) }
                        entry<Screen.WorkspaceFileEditor> { WorkspaceFileEditorPage(it.id, it.area, it.path) }
                        entry<Screen.SkillDetail> { SkillDetailPage(it.skillName) }
                        entry<Screen.MessageSearch> { SearchPage() }
                        entry<Screen.Stats> { StatsPage() }
                    },
                )
                if (buildInfo.debug) {
                    Text(
                        text = "[开发模式]",
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    )
                }
                AnimatedVisibility(
                    visible = migrationState is MigrationState.Migrating,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    val state = migrationState as? MigrationState.Migrating
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            CircularProgressIndicator()
                            Text(
                                text = stringResource(Res.string.db_migrating),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            if (state != null) {
                                Text(
                                    text = "v${state.from} → v${state.to}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun UnavailableRoute(screen: NavKey) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BackButton()
        Text(screen::class.simpleName ?: "Unavailable route", style = MaterialTheme.typography.headlineSmall)
        Text(
            "This feature is not available on ${currentPlatformKind.displayName} yet.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
