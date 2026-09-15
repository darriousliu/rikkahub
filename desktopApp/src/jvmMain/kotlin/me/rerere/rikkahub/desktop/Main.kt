package me.rerere.rikkahub.desktop

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import dev.nucleusframework.application.DecoratedWindow
import dev.nucleusframework.application.NucleusBackend
import dev.nucleusframework.application.nucleusApplication
import dev.nucleusframework.window.TitleBar
import dev.nucleusframework.window.styling.LocalTitleBarStyle
import io.github.vinceglb.filekit.FileKit
import me.rerere.rikkahub.AppRoutes
import me.rerere.rikkahub.di.initKoin
import me.rerere.rikkahub.di.jvmModule
import me.rerere.rikkahub.platform.initializeDesktopImageLoader
import me.rerere.rikkahub.ui.pages.safemode.SafeModePage
import me.rerere.rikkahub.ui.theme.RikkahubTheme
import me.rerere.rikkahub.utils.CrashHandler
import org.koin.core.context.stopKoin
import java.awt.GraphicsEnvironment
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    FileKit.init("RikkaHub")
    if (GraphicsEnvironment.isHeadless()) {
        return
    }

    CrashHandler.install()
    val hasCrashed = CrashHandler.hasCrashed()
    val stackTrace = if (hasCrashed) CrashHandler.getStackTrace() else null
    if (hasCrashed) CrashHandler.clearCrashed()

    initializeDesktopImageLoader()
    initKoin { modules(jvmModule) }
    try {
        nucleusApplication(
            args = args,
            backend = NucleusBackend.Tao,
            // Preserve the existing ability to open independent app processes.
            enableSingleInstance = false,
        ) {
            var showSafeMode by remember { mutableStateOf(hasCrashed) }
            DecoratedWindow(
                onCloseRequest = ::exitApplication,
                title = "RikkaHub",
                icon = painterResource("icons/RikkaHub.png"),
            ) {
                RikkahubTheme {
                    TitleBar { Text("RikkaHub", color = LocalTitleBarStyle.current.colors.content) }
                    if (showSafeMode) {
                        SafeModePage(stackTrace = stackTrace, onEnterApp = { showSafeMode = false })
                    } else {
                        AppRoutes()
                    }
                }
            }
        }
    } finally {
        stopKoin()
    }
    exitProcess(0)
}
