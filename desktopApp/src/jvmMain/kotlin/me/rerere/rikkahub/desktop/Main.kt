package me.rerere.rikkahub.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.github.vinceglb.filekit.FileKit
import java.awt.GraphicsEnvironment
import me.rerere.rikkahub.AppRoutes
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.di.createJvmAppModule
import me.rerere.rikkahub.ui.pages.safemode.SafeModePage
import me.rerere.rikkahub.ui.theme.RikkahubTheme
import me.rerere.rikkahub.utils.CrashHandler
import org.koin.compose.KoinApplication
import org.koin.dsl.koinConfiguration

internal enum class DesktopLaunchMode {
    Interactive,
    Smoke,
}

internal data class DesktopLaunchPolicy(
    val mode: DesktopLaunchMode,
    val shouldOpenWindow: Boolean,
)

internal fun desktopLaunchPolicy(
    args: Array<String>,
    isHeadless: Boolean,
): DesktopLaunchPolicy = DesktopLaunchPolicy(
    mode = if ("--smoke" in args) DesktopLaunchMode.Smoke else DesktopLaunchMode.Interactive,
    shouldOpenWindow = !isHeadless,
)

fun main(args: Array<String>) {
    FileKit.init("RikkaHub")
    val policy = desktopLaunchPolicy(
        args = args,
        isHeadless = GraphicsEnvironment.isHeadless(),
    )
    if (!policy.shouldOpenWindow) {
        return
    }

    CrashHandler.install()
    val hasCrashed = CrashHandler.hasCrashed()
    val stackTrace = if (hasCrashed) CrashHandler.getStackTrace() else null
    if (hasCrashed) CrashHandler.clearCrashed()

    application {
        var showSafeMode by remember { mutableStateOf(hasCrashed) }
        Window(
            onCloseRequest = ::exitApplication,
            title = "RikkaHub",
        ) {
            val appScope = rememberCoroutineScope()
            val configuration = remember(appScope) {
                koinConfiguration { modules(createJvmAppModule(appScope)) }
            }
            KoinApplication(configuration = configuration) {
                RikkahubTheme {
                    if (showSafeMode) {
                        SafeModePage(stackTrace = stackTrace, onEnterApp = { showSafeMode = false })
                    } else {
                        AppRoutes(startScreen = if (policy.mode == DesktopLaunchMode.Smoke) Screen.History else null)
                    }
                }
            }

            if (policy.mode == DesktopLaunchMode.Smoke) {
                LaunchedEffect(Unit) {
                    withFrameNanos { }
                    exitApplication()
                }
            }
        }
    }
}
