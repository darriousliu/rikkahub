package me.rerere.rikkahub

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavKey
import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.cachecontrol.CacheControlCacheStrategy
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import io.ktor.client.HttpClient
import me.rerere.rikkahub.platform.addPlatformGifDecoder
import me.rerere.rikkahub.ui.activity.SafeModeActivity
import me.rerere.rikkahub.ui.context.LocalASRState
import me.rerere.rikkahub.ui.hooks.readBooleanPreference
import me.rerere.rikkahub.ui.hooks.readStringPreference
import me.rerere.rikkahub.ui.hooks.rememberCustomAsrState
import me.rerere.rikkahub.ui.hooks.rememberCustomTtsState
import me.rerere.rikkahub.ui.theme.RikkahubTheme
import me.rerere.rikkahub.utils.CrashHandler
import me.rerere.rikkahub.utils.openUsageAccessSettings
import org.koin.android.ext.android.inject
import me.rerere.rikkahub.ui.pages.debug.DebugPage
import me.rerere.rikkahub.ui.pages.extensions.workspace.WorkspaceDetailPage
import me.rerere.rikkahub.ui.pages.extensions.workspace.WorkspaceFileEditorPage
import me.rerere.rikkahub.ui.pages.extensions.workspace.WorkspacePage
import me.rerere.rikkahub.ui.pages.extensions.workspace.WorkspaceTerminalPage
import me.rerere.rikkahub.ui.pages.setting.SettingFilesPage
import me.rerere.rikkahub.ui.pages.share.handler.ShareHandlerPage
import me.rerere.workspace.WorkspaceStorageArea
import kotlin.uuid.Uuid

class RouteActivity : ComponentActivity() {
    private val httpClient by inject<HttpClient>()
    private var navStack: MutableList<NavKey>? = null

    // Volume key listener registry — last registered handler wins.
    internal val volumeKeyListeners = mutableListOf<(isVolumeUp: Boolean) -> Boolean>()

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val isVolumeUp = when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> true
                KeyEvent.KEYCODE_VOLUME_DOWN -> false
                else -> return super.dispatchKeyEvent(event)
            }
            if (volumeKeyListeners.lastOrNull()?.invoke(isVolumeUp) == true) return true
        }
        return super.dispatchKeyEvent(event)
    }

    @OptIn(ExperimentalCoilApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        disableNavigationBarContrast()
        super.onCreate(savedInstanceState)
        if (CrashHandler.hasCrashed(this)) {
            startActivity(Intent(this, SafeModeActivity::class.java))
            finish()
            return
        }
        setContent {
            RikkahubTheme {
                setSingletonImageLoaderFactory { context ->
                    ImageLoader.Builder(context)
                        .crossfade(true)
                        .components {
                            add(
                                KtorNetworkFetcherFactory(
                                    httpClient = { httpClient },
                                    cacheStrategy = { CacheControlCacheStrategy() },
                                ),
                            )
                            addPlatformGifDecoder()
                            add(SvgDecoder.Factory(scaleToDensity = true))
                        }
                        .build()
                }
                AppRoutes()
            }
        }
    }

    private fun disableNavigationBarContrast() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
    }

    private fun initialShareScreen(): Screen.ShareHandler? = when (intent?.action) {
        Intent.ACTION_SEND -> Screen.ShareHandler(
            text = intent?.getStringExtra(Intent.EXTRA_TEXT).orEmpty(),
            streamUri = intent?.getStringExtra(Intent.EXTRA_STREAM),
        )

        Intent.ACTION_PROCESS_TEXT -> Screen.ShareHandler(
            text = intent?.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString().orEmpty(),
        )

        else -> null
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        conversationScreen(intent.getStringExtra(CONVERSATION_ID_EXTRA))?.let { screen ->
            navStack?.add(screen)
        }
    }

    @Composable
    private fun AppRoutes() {
        val tts = rememberCustomTtsState()
        val asr = rememberCustomAsrState()
        val startScreen = remember {
            Screen.Chat(
                id = if (readBooleanPreference("create_new_conversation_on_start", true)) {
                    Uuid.random().toString()
                } else {
                    readStringPreference(
                        "lastConversationId",
                        Uuid.random().toString(),
                    ) ?: Uuid.random().toString()
                },
            )
        }
        val shareScreen = remember { initialShareScreen() }
        var shareHandled by remember { mutableStateOf(false) }

        CompositionLocalProvider(LocalASRState provides asr) {
            me.rerere.rikkahub.AppRoutes(
                startScreen = startScreen,
                ttsState = tts,
                platformEntries = {
                    entry<Screen.ShareHandler> { ShareHandlerPage(it.text, it.streamUri) }
                    entry<Screen.SettingFiles> { SettingFilesPage() }
                    entry<Screen.Debug> { DebugPage() }
                    entry<Screen.Workspaces> { WorkspacePage() }
                    entry<Screen.WorkspaceDetail> { WorkspaceDetailPage(it.id) }
                    entry<Screen.WorkspaceTerminal> { WorkspaceTerminalPage(it.id) }
                    entry<Screen.WorkspaceFileEditor> {
                        WorkspaceFileEditorPage(it.id, WorkspaceStorageArea.valueOf(it.area), it.path)
                    }
                },
                onOpenUsageAccessSettings = { openUsageAccessSettings() },
                onBackStackChanged = { backStack ->
                    navStack = backStack
                    if (!shareHandled) {
                        shareHandled = true
                        shareScreen?.let(backStack::add)
                    }
                },
            )
        }
    }
}
