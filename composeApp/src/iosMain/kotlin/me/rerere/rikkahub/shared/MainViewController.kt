package me.rerere.rikkahub.shared

import androidx.compose.foundation.ComposeFoundationFlags
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.channels.Channel
import me.rerere.rikkahub.AppRoutes
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.ai.transformers.PdfTextExtractor
import me.rerere.rikkahub.di.createIosAppModule
import me.rerere.rikkahub.ui.pages.safemode.SafeModePage
import me.rerere.rikkahub.ui.theme.RikkahubTheme
import me.rerere.rikkahub.utils.CrashHandler
import org.koin.compose.KoinApplication
import org.koin.dsl.koinConfiguration
import platform.UIKit.UIViewController

private val sharedTexts = Channel<String>(Channel.UNLIMITED)

fun receiveSharedText(text: String) {
    sharedTexts.trySend(text)
}

/** UIKit bridge used by the iOS application shell. */
@OptIn(ExperimentalFoundationApi::class)
fun MainViewController(pdfTextExtractor: PdfTextExtractor): UIViewController {
    // CMP 1.12 disables menu extensions by default; image paste needs the native menu extension.
    ComposeFoundationFlags.isNewContextMenuEnabled = true
    CrashHandler.install()
    val hasCrashed = CrashHandler.hasCrashed()
    val stackTrace = if (hasCrashed) CrashHandler.getStackTrace() else null
    if (hasCrashed) CrashHandler.clearCrashed()
    return ComposeUIViewController {
        var showSafeMode by remember { mutableStateOf(hasCrashed) }
        var navStack by remember { mutableStateOf<MutableList<NavKey>?>(null) }
        LaunchedEffect(navStack) {
            val stack = navStack ?: return@LaunchedEffect
            for (text in sharedTexts) stack.add(Screen.ShareHandler(text))
        }
        val appScope = rememberCoroutineScope()
        val configuration = remember(appScope, pdfTextExtractor) {
            koinConfiguration { modules(createIosAppModule(appScope, pdfTextExtractor)) }
        }
        KoinApplication(configuration = configuration) {
            RikkahubTheme {
                if (showSafeMode) {
                    SafeModePage(stackTrace = stackTrace, onEnterApp = { showSafeMode = false })
                } else {
                    AppRoutes(onBackStackChanged = { navStack = it })
                }
            }
        }
    }
}
