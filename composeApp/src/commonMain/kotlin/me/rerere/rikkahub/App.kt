package me.rerere.rikkahub

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDeepLink
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import co.touchlab.kermit.Logger
import coil3.ImageLoader
import coil3.compose.LocalPlatformContext
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import coil3.toUri
import com.dokar.sonner.Toaster
import com.dokar.sonner.rememberToasterState
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import me.rerere.highlight.Highlighter
import me.rerere.highlight.LocalHighlighter
import me.rerere.rikkahub.buildkonfig.BuildConfig
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.di.HttpClientType
import me.rerere.rikkahub.ui.components.ui.TTSController
import me.rerere.rikkahub.ui.context.LocalAnimatedVisibilityScope
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalSharedTransitionScope
import me.rerere.rikkahub.ui.context.LocalTTSState
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.readBooleanPreference
import me.rerere.rikkahub.ui.hooks.readStringPreference
import me.rerere.rikkahub.ui.hooks.rememberCustomTtsState
import me.rerere.rikkahub.ui.pages.assistant.AssistantPage
import me.rerere.rikkahub.ui.pages.assistant.detail.AssistantBasicPage
import me.rerere.rikkahub.ui.pages.assistant.detail.AssistantDetailPage
import me.rerere.rikkahub.ui.pages.assistant.detail.AssistantInjectionsPage
import me.rerere.rikkahub.ui.pages.assistant.detail.AssistantLocalToolPage
import me.rerere.rikkahub.ui.pages.assistant.detail.AssistantMcpPage
import me.rerere.rikkahub.ui.pages.assistant.detail.AssistantMemoryPage
import me.rerere.rikkahub.ui.pages.assistant.detail.AssistantPromptPage
import me.rerere.rikkahub.ui.pages.assistant.detail.AssistantRequestPage
import me.rerere.rikkahub.ui.pages.backup.BackupPage
import me.rerere.rikkahub.ui.pages.chat.ChatPage
import me.rerere.rikkahub.ui.pages.debug.DebugPage
import me.rerere.rikkahub.ui.pages.developer.DeveloperPage
import me.rerere.rikkahub.ui.pages.history.HistoryPage
import me.rerere.rikkahub.ui.pages.imggen.ImageGenPage
import me.rerere.rikkahub.ui.pages.log.LogPage
import me.rerere.rikkahub.ui.pages.menu.MenuPage
import me.rerere.rikkahub.ui.pages.prompts.PromptPage
import me.rerere.rikkahub.ui.pages.setting.SettingAboutPage
import me.rerere.rikkahub.ui.pages.setting.SettingDisplayPage
import me.rerere.rikkahub.ui.pages.setting.SettingDonatePage
import me.rerere.rikkahub.ui.pages.setting.SettingFilesPage
import me.rerere.rikkahub.ui.pages.setting.SettingMcpPage
import me.rerere.rikkahub.ui.pages.setting.SettingModelPage
import me.rerere.rikkahub.ui.pages.setting.SettingPage
import me.rerere.rikkahub.ui.pages.setting.SettingProviderDetailPage
import me.rerere.rikkahub.ui.pages.setting.SettingProviderPage
import me.rerere.rikkahub.ui.pages.setting.SettingSearchPage
import me.rerere.rikkahub.ui.pages.setting.SettingTTSPage
import me.rerere.rikkahub.ui.pages.share.handler.ShareHandlerPage
import me.rerere.rikkahub.ui.pages.translator.TranslatorPage
import me.rerere.rikkahub.ui.pages.webview.WebViewPage
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.ui.theme.RikkahubTheme
import org.koin.compose.getKoin
import org.koin.compose.koinInject
import org.koin.core.qualifier.qualifier
import kotlin.jvm.JvmSuppressWildcards
import kotlin.reflect.KType
import kotlin.uuid.Uuid

private const val TAG = "RikkaHubApp"

const val CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID = "chat_completed"
const val CHAT_LIVE_UPDATE_NOTIFICATION_CHANNEL_ID = "chat_live_update"

@Composable
fun App(
    navBackStack: NavHostController = rememberNavController(),
    platformConfigure: @Composable (darkTheme: Boolean) -> Unit = {},
) {
    val koin = getKoin()
    val highlighter = koinInject<Highlighter>()
    val settingsStore = koinInject<SettingsStore>()
    val context = LocalPlatformContext.current
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .crossfade(true)
            .components {
                add(KtorNetworkFetcherFactory(koin.get<HttpClient>(qualifier(HttpClientType.Chat))))
                add(SvgDecoder.Factory(scaleToDensity = true))
            }
            .build()
    }
    RikkahubTheme(
        platformConfigure = platformConfigure,
    ) {
        val toastState = rememberToasterState()
        val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
        val tts = rememberCustomTtsState()
        SharedTransitionLayout {
            CompositionLocalProvider(
                LocalNavController provides navBackStack,
                LocalSharedTransitionScope provides this,
                LocalSettings provides settings,
                LocalHighlighter provides highlighter,
                LocalToaster provides toastState,
                LocalTTSState provides tts,
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
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    NavHost(
                        modifier = Modifier
                            .fillMaxSize(),
                        startDestination = Screen.Chat(
                        id = if (context.readBooleanPreference("create_new_conversation_on_start", true)) {
                            Uuid.random().toString()
                        } else {
                            context.readStringPreference(
                                "lastConversationId",
                                Uuid.random().toString()
                            ) ?: Uuid.random().toString()
                        }
                    ),
                    navController = navBackStack,
                    enterTransition = { slideInHorizontally(initialOffsetX = { it }) },
                    exitTransition = { slideOutHorizontally(targetOffsetX = { -it }) },
                    popEnterTransition = {
                        slideInHorizontally(initialOffsetX = { -it / 2 }) + scaleIn(initialScale = 1.3f) + fadeIn()
                    },
                    popExitTransition = {
                        slideOutHorizontally(targetOffsetX = { it }) + scaleOut(targetScale = 0.75f) + fadeOut()
                    }
                ) {
                    composable<Screen.Chat>(
                        enterTransition = { fadeIn() },
                        exitTransition = { fadeOut() },
                    ) { backStackEntry ->
                        val route = backStackEntry.toRoute<Screen.Chat>()
                        ChatPage(
                            id = Uuid.parse(route.id),
                            text = route.text,
                            files = route.files.map { it.toUri() }
                        )
                    }

                    composable<Screen.ShareHandler> { backStackEntry ->
                        val route = backStackEntry.toRoute<Screen.ShareHandler>()
                        ShareHandlerPage(
                            text = route.text,
                            image = route.streamUri
                        )
                    }

                    composable<Screen.History> {
                        HistoryPage()
                    }

                    composableWrapper<Screen.Assistant> {
                        AssistantPage()
                    }

                    composableWrapper<Screen.AssistantDetail> { backStackEntry ->
                        val route = backStackEntry.toRoute<Screen.AssistantDetail>()
                        AssistantDetailPage(route.id)
                    }

                    composableWrapper<Screen.AssistantBasic> { backStackEntry ->
                        val route = backStackEntry.toRoute<Screen.AssistantBasic>()
                        AssistantBasicPage(route.id)
                    }

                    composable<Screen.AssistantPrompt> { backStackEntry ->
                        val route = backStackEntry.toRoute<Screen.AssistantPrompt>()
                        AssistantPromptPage(route.id)
                    }

                    composable<Screen.AssistantMemory> { backStackEntry ->
                        val route = backStackEntry.toRoute<Screen.AssistantMemory>()
                        AssistantMemoryPage(route.id)
                    }

                    composable<Screen.AssistantRequest> { backStackEntry ->
                        val route = backStackEntry.toRoute<Screen.AssistantRequest>()
                        AssistantRequestPage(route.id)
                    }

                    composable<Screen.AssistantMcp> { backStackEntry ->
                        val route = backStackEntry.toRoute<Screen.AssistantMcp>()
                        AssistantMcpPage(route.id)
                    }

                    composable<Screen.AssistantLocalTool> { backStackEntry ->
                        val route = backStackEntry.toRoute<Screen.AssistantLocalTool>()
                        AssistantLocalToolPage(route.id)
                    }

                    composable<Screen.AssistantInjections> { backStackEntry ->
                        val route = backStackEntry.toRoute<Screen.AssistantInjections>()
                        AssistantInjectionsPage(route.id)
                    }

                    composable<Screen.Menu> {
                        MenuPage()
                    }

                    composable<Screen.Translator> {
                        TranslatorPage()
                    }

                    composable<Screen.Setting> {
                        SettingPage()
                    }

                    composable<Screen.Backup> {
                        BackupPage()
                    }

                    composable<Screen.ImageGen> {
                        ImageGenPage()
                    }

                    composable<Screen.WebView> { backStackEntry ->
                        val route = backStackEntry.toRoute<Screen.WebView>()
                        WebViewPage(route.url, route.content)
                    }

                    composable<Screen.SettingDisplay> {
                        SettingDisplayPage()
                    }

                    composable<Screen.SettingProvider> {
                        SettingProviderPage()
                    }

                    composable<Screen.SettingProviderDetail> {
                        val route = it.toRoute<Screen.SettingProviderDetail>()
                        val id = Uuid.parse(route.providerId)
                        SettingProviderDetailPage(id = id)
                    }

                    composable<Screen.SettingModels> {
                        SettingModelPage()
                    }

                    composable<Screen.SettingAbout> {
                        SettingAboutPage()
                    }

                    composable<Screen.SettingSearch> {
                        SettingSearchPage()
                    }

                    composable<Screen.SettingTTS> {
                        SettingTTSPage()
                    }

                    composable<Screen.SettingMcp> {
                        SettingMcpPage()
                    }

                    composable<Screen.SettingDonate> {
                        SettingDonatePage()
                    }

                        composable<Screen.SettingFiles> {
                            SettingFilesPage()
                        }

                    composable<Screen.Developer> {
                        DeveloperPage()
                    }

                    composable<Screen.Debug> {
                        DebugPage()
                    }

                    composable<Screen.Log> {
                        LogPage()
                    }

                    composable<Screen.Prompts> {
                        PromptPage()
                    }
                }
                    if (BuildConfig.DEBUG) {
                        Text(
                            text = "[开发模式]",
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

inline fun <reified T : Any> NavGraphBuilder.composableWrapper(
    typeMap: Map<KType, @JvmSuppressWildcards NavType<*>> = emptyMap(),
    deepLinks: List<NavDeepLink> = emptyList(),
    noinline enterTransition:
    (AnimatedContentTransitionScope<NavBackStackEntry>.() -> @JvmSuppressWildcards
    EnterTransition?)? =
        null,
    noinline exitTransition:
    (AnimatedContentTransitionScope<NavBackStackEntry>.() -> @JvmSuppressWildcards
    ExitTransition?)? =
        null,
    noinline popEnterTransition:
    (AnimatedContentTransitionScope<NavBackStackEntry>.() -> @JvmSuppressWildcards
    EnterTransition?)? =
        enterTransition,
    noinline popExitTransition:
    (AnimatedContentTransitionScope<NavBackStackEntry>.() -> @JvmSuppressWildcards
    ExitTransition?)? =
        exitTransition,
    noinline sizeTransform:
    (AnimatedContentTransitionScope<NavBackStackEntry>.() -> @JvmSuppressWildcards
    SizeTransform?)? =
        null,
    noinline content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit
) {
    composable(
        route = T::class,
        typeMap = typeMap,
        deepLinks = deepLinks,
        enterTransition = enterTransition,
        exitTransition = exitTransition,
        popEnterTransition = popEnterTransition,
        popExitTransition = popExitTransition,
        sizeTransform = sizeTransform,
        content = {
            CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                content(it)
            }
        }
    )
}



class AppScope : CoroutineScope by CoroutineScope(
    SupervisorJob()
        + Dispatchers.Main
        + CoroutineName("AppScope")
        + CoroutineExceptionHandler { _, e ->
        Logger.e(TAG, e) { "AppScope exception" }
    }
)
