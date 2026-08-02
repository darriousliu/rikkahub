package me.rerere.rikkahub.shared

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.dokar.sonner.Toaster
import com.dokar.sonner.ToasterState
import com.dokar.sonner.rememberToasterState
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.db.DatabaseMigrationTracker
import me.rerere.rikkahub.data.db.MigrationState
import me.rerere.rikkahub.data.event.AppEvent
import me.rerere.rikkahub.data.event.AppEventBus
import me.rerere.rikkahub.generated.resources.Res
import me.rerere.rikkahub.generated.resources.db_migrating
import me.rerere.rikkahub.ui.components.richtext.LocalRichTextPlatformActions
import me.rerere.rikkahub.ui.components.richtext.RichTextPlatformActions
import me.rerere.rikkahub.ui.components.ui.LocalImageSaveHandler
import me.rerere.rikkahub.ui.components.ui.TTSController
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalSharedTransitionScope
import me.rerere.rikkahub.ui.context.LocalTTSState
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.context.Navigator
import me.rerere.rikkahub.ui.hooks.CustomTtsState
import me.rerere.rikkahub.ui.pages.assistant.AssistantPage
import me.rerere.rikkahub.ui.pages.assistant.detail.AssistantBasicPage
import me.rerere.rikkahub.ui.pages.assistant.detail.AssistantDetailPage
import me.rerere.rikkahub.ui.pages.assistant.detail.AssistantMcpPage
import me.rerere.rikkahub.ui.pages.assistant.detail.AssistantMemoryPage
import me.rerere.rikkahub.ui.pages.assistant.detail.AssistantRequestPage
import me.rerere.rikkahub.ui.pages.backup.BackupPage
import me.rerere.rikkahub.ui.pages.extensions.ExtensionsPage
import me.rerere.rikkahub.ui.pages.extensions.PromptPage
import me.rerere.rikkahub.ui.pages.extensions.QuickMessagesPage
import me.rerere.rikkahub.ui.pages.extensions.skills.SkillDetailPage
import me.rerere.rikkahub.ui.pages.extensions.skills.SkillsPage
import me.rerere.rikkahub.ui.pages.favorite.FavoritePage
import me.rerere.rikkahub.ui.pages.history.HistoryPage
import me.rerere.rikkahub.ui.pages.imggen.ImageGenPage
import me.rerere.rikkahub.ui.pages.setting.SettingAboutPage
import me.rerere.rikkahub.ui.pages.setting.SettingDonatePage
import me.rerere.rikkahub.ui.pages.setting.SettingMcpPage
import me.rerere.rikkahub.ui.pages.setting.SettingPage
import me.rerere.rikkahub.ui.pages.setting.SettingModelPage
import me.rerere.rikkahub.ui.pages.setting.SettingPreferencesNotificationPage
import me.rerere.rikkahub.ui.pages.setting.SettingPreferencesPage
import me.rerere.rikkahub.ui.pages.setting.SettingPreferencesGeneralPage
import me.rerere.rikkahub.ui.pages.setting.SettingPreferencesThemePage
import me.rerere.rikkahub.ui.pages.setting.SettingPreferencesUIPage
import me.rerere.rikkahub.ui.pages.setting.SettingProviderDetailPage
import me.rerere.rikkahub.ui.pages.setting.SettingProviderPage
import me.rerere.rikkahub.ui.pages.setting.SettingSearchDetailPage
import me.rerere.rikkahub.ui.pages.setting.SettingSearchPage
import me.rerere.rikkahub.ui.pages.setting.SettingSpeechPage
import me.rerere.rikkahub.ui.pages.setting.SettingThemePage
import me.rerere.rikkahub.ui.pages.setting.SettingWebPage
import me.rerere.rikkahub.ui.pages.stats.StatsPage
import me.rerere.rikkahub.ui.pages.translator.TranslatorPage
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import org.koin.compose.koinInject
import org.jetbrains.compose.resources.stringResource
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import kotlin.uuid.Uuid

/** Renders routes that intentionally remain in a platform application shell. */
interface PlatformRouteContent {
    @Composable
    fun Render(screen: Screen)
}

private val navigationSavedStateConfiguration = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(Screen.Chat::class, Screen.Chat.serializer())
            subclass(Screen.ShareHandler::class, Screen.ShareHandler.serializer())
            subclass(Screen.History::class, Screen.History.serializer())
            subclass(Screen.Favorite::class, Screen.Favorite.serializer())
            subclass(Screen.Assistant::class, Screen.Assistant.serializer())
            subclass(Screen.AssistantDetail::class, Screen.AssistantDetail.serializer())
            subclass(Screen.AssistantBasic::class, Screen.AssistantBasic.serializer())
            subclass(Screen.AssistantPrompt::class, Screen.AssistantPrompt.serializer())
            subclass(Screen.AssistantMemory::class, Screen.AssistantMemory.serializer())
            subclass(Screen.AssistantRequest::class, Screen.AssistantRequest.serializer())
            subclass(Screen.AssistantMcp::class, Screen.AssistantMcp.serializer())
            subclass(Screen.AssistantLocalTool::class, Screen.AssistantLocalTool.serializer())
            subclass(Screen.AssistantInjections::class, Screen.AssistantInjections.serializer())
            subclass(Screen.Translator::class, Screen.Translator.serializer())
            subclass(Screen.Setting::class, Screen.Setting.serializer())
            subclass(Screen.Backup::class, Screen.Backup.serializer())
            subclass(Screen.ImageGen::class, Screen.ImageGen.serializer())
            subclass(Screen.WebView::class, Screen.WebView.serializer())
            subclass(Screen.SettingTheme::class, Screen.SettingTheme.serializer())
            subclass(Screen.SettingPreferences::class, Screen.SettingPreferences.serializer())
            subclass(Screen.SettingPreferencesTheme::class, Screen.SettingPreferencesTheme.serializer())
            subclass(Screen.SettingPreferencesNotification::class, Screen.SettingPreferencesNotification.serializer())
            subclass(Screen.SettingPreferencesGeneral::class, Screen.SettingPreferencesGeneral.serializer())
            subclass(Screen.SettingPreferencesUI::class, Screen.SettingPreferencesUI.serializer())
            subclass(Screen.SettingProvider::class, Screen.SettingProvider.serializer())
            subclass(Screen.SettingProviderDetail::class, Screen.SettingProviderDetail.serializer())
            subclass(Screen.SettingModels::class, Screen.SettingModels.serializer())
            subclass(Screen.SettingAbout::class, Screen.SettingAbout.serializer())
            subclass(Screen.SettingSearch::class, Screen.SettingSearch.serializer())
            subclass(Screen.SettingSearchDetail::class, Screen.SettingSearchDetail.serializer())
            subclass(Screen.SettingSpeech::class, Screen.SettingSpeech.serializer())
            subclass(Screen.SettingMcp::class, Screen.SettingMcp.serializer())
            subclass(Screen.SettingDonate::class, Screen.SettingDonate.serializer())
            subclass(Screen.SettingFiles::class, Screen.SettingFiles.serializer())
            subclass(Screen.SettingWeb::class, Screen.SettingWeb.serializer())
            subclass(Screen.Debug::class, Screen.Debug.serializer())
            subclass(Screen.Log::class, Screen.Log.serializer())
            subclass(Screen.Extensions::class, Screen.Extensions.serializer())
            subclass(Screen.QuickMessages::class, Screen.QuickMessages.serializer())
            subclass(Screen.Prompts::class, Screen.Prompts.serializer())
            subclass(Screen.Skills::class, Screen.Skills.serializer())
            subclass(Screen.Workspaces::class, Screen.Workspaces.serializer())
            subclass(Screen.WorkspaceDetail::class, Screen.WorkspaceDetail.serializer())
            subclass(Screen.WorkspaceTerminal::class, Screen.WorkspaceTerminal.serializer())
            subclass(Screen.WorkspaceFileEditor::class, Screen.WorkspaceFileEditor.serializer())
            subclass(Screen.SkillDetail::class, Screen.SkillDetail.serializer())
            subclass(Screen.MessageSearch::class, Screen.MessageSearch.serializer())
            subclass(Screen.Stats::class, Screen.Stats.serializer())
        }
    }
}

/**
 * Shared product navigation and application-level UI context.
 *
 * Platform shells provide only system actions and the routes that cannot live in common code.
 */
@Composable
fun ProductNavigationHost(
    startScreen: Screen,
    ttsState: CustomTtsState,
    platformRoutes: PlatformRouteContent,
    modifier: Modifier = Modifier,
    richTextPlatformActions: @Composable (Navigator) -> RichTextPlatformActions = { RichTextPlatformActions() },
    imageSaveHandler: (suspend (String, ToasterState) -> Unit)? = null,
    onOpenUsageAccessSettings: () -> Unit = {},
    onBackStackChanged: (MutableList<NavKey>) -> Unit = {},
) {
    val toastState = rememberToasterState()
    val settingsStore = koinInject<SettingsStore>()
    val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
    val eventBus = koinInject<AppEventBus>()
    val buildInfo = koinInject<PlatformBuildInfo>()
    val migrationState by DatabaseMigrationTracker.state.collectAsStateWithLifecycle()
    val backStack = rememberNavBackStack(navigationSavedStateConfiguration, startScreen)
    val navigator = remember(backStack) { Navigator(backStack) }
    val resolvedRichTextPlatformActions = richTextPlatformActions(navigator)

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
            LocalImageSaveHandler provides imageSaveHandler?.let { handler ->
                { imageUrl -> handler(imageUrl, toastState) }
            },
            LocalRichTextPlatformActions provides resolvedRichTextPlatformActions,
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
                    entryProvider = entryProvider {
                        entry<Screen.Chat> { platformRoutes.Render(it) }
                        entry<Screen.ShareHandler> { platformRoutes.Render(it) }
                        entry<Screen.History> { HistoryPage() }
                        entry<Screen.Favorite> { FavoritePage() }
                        entry<Screen.Assistant> { AssistantPage() }
                        entry<Screen.AssistantDetail> { AssistantDetailPage(it.id) }
                        entry<Screen.AssistantBasic> { AssistantBasicPage(it.id) }
                        entry<Screen.AssistantPrompt> { platformRoutes.Render(it) }
                        entry<Screen.AssistantMemory> { AssistantMemoryPage(it.id) }
                        entry<Screen.AssistantRequest> { AssistantRequestPage(it.id) }
                        entry<Screen.AssistantMcp> { AssistantMcpPage(it.id) }
                        entry<Screen.AssistantLocalTool> { platformRoutes.Render(it) }
                        entry<Screen.AssistantInjections> { platformRoutes.Render(it) }
                        entry<Screen.Translator> { TranslatorPage() }
                        entry<Screen.Setting> { SettingPage() }
                        entry<Screen.Backup> { BackupPage() }
                        entry<Screen.ImageGen> { ImageGenPage() }
                        entry<Screen.WebView> { platformRoutes.Render(it) }
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
                        entry<Screen.SettingFiles> { platformRoutes.Render(it) }
                        entry<Screen.SettingWeb> { SettingWebPage() }
                        entry<Screen.Debug> { platformRoutes.Render(it) }
                        entry<Screen.Log> { platformRoutes.Render(it) }
                        entry<Screen.Extensions> { ExtensionsPage() }
                        entry<Screen.QuickMessages> { QuickMessagesPage() }
                        entry<Screen.Prompts> { PromptPage() }
                        entry<Screen.Skills> { SkillsPage() }
                        if (hasCapability(currentPlatformKind, PlatformCapability.WORKSPACE)) {
                            entry<Screen.Workspaces> { platformRoutes.Render(it) }
                            entry<Screen.WorkspaceDetail> { platformRoutes.Render(it) }
                            entry<Screen.WorkspaceTerminal> { platformRoutes.Render(it) }
                            entry<Screen.WorkspaceFileEditor> { platformRoutes.Render(it) }
                        }
                        entry<Screen.SkillDetail> { SkillDetailPage(it.skillName) }
                        entry<Screen.MessageSearch> { platformRoutes.Render(it) }
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
