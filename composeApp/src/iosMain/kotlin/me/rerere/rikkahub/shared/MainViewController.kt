package me.rerere.rikkahub.shared

import androidx.compose.foundation.ComposeFoundationFlags
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import me.rerere.rikkahub.AppRoutes
import me.rerere.rikkahub.di.createIosAppModule
import me.rerere.rikkahub.ui.pages.safemode.SafeModePage
import me.rerere.rikkahub.ui.theme.RikkahubTheme
import me.rerere.rikkahub.utils.CrashHandler
import org.koin.compose.KoinApplication
import org.koin.dsl.koinConfiguration
import platform.UIKit.UIViewController

/** UIKit bridge used by the iOS application shell. */
@OptIn(ExperimentalFoundationApi::class)
fun MainViewController(): UIViewController {
    // CMP 1.12 disables menu extensions by default; image paste needs the native menu extension.
    ComposeFoundationFlags.isNewContextMenuEnabled = true
    CrashHandler.install()
    val hasCrashed = CrashHandler.hasCrashed()
    val stackTrace = if (hasCrashed) CrashHandler.getStackTrace() else null
    if (hasCrashed) CrashHandler.clearCrashed()
    return ComposeUIViewController {
        var showSafeMode by remember { mutableStateOf(hasCrashed) }
        val appScope = rememberCoroutineScope()
        val configuration = remember(appScope) {
            koinConfiguration { modules(createIosAppModule(appScope)) }
        }
        KoinApplication(configuration = configuration) {
            RikkahubTheme {
                if (showSafeMode) {
                    SafeModePage(stackTrace = stackTrace, onEnterApp = { showSafeMode = false })
                } else {
                    AppRoutes()
                }
            }
        }
    }
}
