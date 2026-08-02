package me.rerere.rikkahub.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import java.awt.GraphicsEnvironment
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.createJvmSettingsDataStore
import me.rerere.rikkahub.platform.JvmExternalUriOpener
import me.rerere.rikkahub.shared.CapabilityState
import me.rerere.rikkahub.shared.PlatformCapability
import me.rerere.rikkahub.shared.PlatformKind
import me.rerere.rikkahub.shared.SharedProductApp
import me.rerere.rikkahub.shared.capabilityMatrix
import me.rerere.rikkahub.shared.currentDesktopPlatformBuildInfo
import me.rerere.rikkahub.shared.currentPlatformKind

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

internal fun validatesHeadlessSharedEntry(): Boolean {
    val platform = currentPlatformKind
    val capabilities = capabilityMatrix(platform)
    return platform == PlatformKind.DESKTOP &&
        capabilities.keys.toList() == PlatformCapability.entries &&
        capabilities[PlatformCapability.SHARED_ENTRY] == CapabilityState.READY
}

fun main(args: Array<String>) {
    val policy = desktopLaunchPolicy(
        args = args,
        isHeadless = GraphicsEnvironment.isHeadless(),
    )
    if (!policy.shouldOpenWindow) {
        if (policy.mode == DesktopLaunchMode.Smoke) {
            check(validatesHeadlessSharedEntry()) {
                "Desktop shared entry capability contract is invalid"
            }
        }
        return
    }

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "RikkaHub",
        ) {
            val appScope = rememberCoroutineScope()
            val settingsStore = remember(appScope) {
                SettingsStore(
                    dataStore = createJvmSettingsDataStore(appScope),
                    scope = appScope,
                )
            }
            SharedProductApp(
                settingsStore = settingsStore,
                buildInfo = currentDesktopPlatformBuildInfo(),
                externalUriOpener = JvmExternalUriOpener(),
            )

            if (policy.mode == DesktopLaunchMode.Smoke) {
                LaunchedEffect(Unit) {
                    withFrameNanos { }
                    exitApplication()
                }
            }
        }
    }
}
