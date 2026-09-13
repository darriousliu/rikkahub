package me.rerere.rikkahub.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.github.vinceglb.filekit.FileKit
import java.awt.GraphicsEnvironment
import me.rerere.rikkahub.AppRoutes
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.di.createJvmAppModule
import me.rerere.rikkahub.ui.theme.RikkahubTheme
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

    application {
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
                    AppRoutes(startScreen = if (policy.mode == DesktopLaunchMode.Smoke) Screen.History else null)
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
