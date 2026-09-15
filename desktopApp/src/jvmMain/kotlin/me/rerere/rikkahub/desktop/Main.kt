package me.rerere.rikkahub.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.material3.Text
import androidx.compose.ui.res.painterResource
import dev.nucleusframework.application.DecoratedWindow
import dev.nucleusframework.application.NucleusBackend
import dev.nucleusframework.application.nucleusApplication
import dev.nucleusframework.window.TitleBar
import dev.nucleusframework.window.styling.LocalTitleBarStyle
import io.github.vinceglb.filekit.FileKit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.AppRoutes
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.di.initKoin
import me.rerere.rikkahub.di.jvmModule
import me.rerere.rikkahub.platform.initializeDesktopImageLoader
import me.rerere.rikkahub.ui.pages.safemode.SafeModePage
import me.rerere.rikkahub.ui.theme.RikkahubTheme
import me.rerere.rikkahub.utils.CrashHandler
import org.koin.core.context.stopKoin
import java.awt.GraphicsEnvironment
import java.io.File
import kotlin.system.exitProcess

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
    // FileKit uses OS directories rather than user.home. Allow isolated smoke/GUI profiles as well.
    val dataDirectory = System.getProperty("rikkahub.dataDir")?.let(::File)
    FileKit.init(
        appId = "RikkaHub",
        filesDir = dataDirectory,
        cacheDir = dataDirectory?.resolve("cache"),
    )
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

    initializeDesktopImageLoader()
    initKoin { modules(jvmModule) }
    try {
        nucleusApplication(
            args = args,
            backend = NucleusBackend.Tao,
            // Preserve the existing ability to open independent app processes/profiles.
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
                        AppRoutes(startScreen = if (policy.mode == DesktopLaunchMode.Smoke) Screen.History else null)
                    }
                }

                if (policy.mode == DesktopLaunchMode.Smoke) {
                    LaunchedEffect(Unit) {
                        val windowThread = Thread.currentThread()
                        withContext(Dispatchers.Main.immediate) {
                            check(Thread.currentThread() === windowThread) {
                                "Dispatchers.Main must run on the Tao window thread"
                            }
                        }
                        withFrameNanos { }
                        println("RikkaHub desktop smoke passed: Tao window and Main dispatcher")
                        exitApplication()
                    }
                }
            }
        }
    } finally {
        stopKoin()
    }
    exitProcess(0)
}
