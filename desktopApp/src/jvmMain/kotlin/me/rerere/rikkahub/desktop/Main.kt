package me.rerere.rikkahub.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import java.awt.GraphicsEnvironment
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.DataStoreBooleanPreferenceStore
import me.rerere.rikkahub.data.datastore.DataStoreStringPreferenceStore
import me.rerere.rikkahub.data.datastore.createJvmSettingsDataStore
import me.rerere.rikkahub.data.db.createJvmAppDatabase
import me.rerere.rikkahub.data.db.defaultJvmDatabaseFile
import me.rerere.rikkahub.data.sync.BackupFileLayout
import me.rerere.rikkahub.platform.JvmExternalUriOpener
import me.rerere.rikkahub.platform.JvmOAuthCallbackSessionFactory
import me.rerere.rikkahub.platform.JvmSentryMonitoring
import me.rerere.rikkahub.platform.JvmSystemTrayChatNotificationPresenter
import me.rerere.rikkahub.shared.CapabilityState
import me.rerere.rikkahub.shared.JvmPlatformRouteContent
import me.rerere.rikkahub.shared.PlatformCapability
import me.rerere.rikkahub.shared.PlatformKind
import me.rerere.rikkahub.shared.SharedProductApp
import me.rerere.rikkahub.shared.capabilityMatrix
import me.rerere.rikkahub.shared.currentDesktopPlatformBuildInfo
import me.rerere.rikkahub.shared.currentPlatformKind
import me.rerere.rikkahub.ui.components.message.JvmChatMessagePlatformActions
import me.rerere.rikkahub.ui.components.richtext.rememberJvmRichTextPlatformActions
import me.rerere.rikkahub.web.createJvmWebServerRuntime
import me.rerere.tts.controller.JvmAudioPlayer
import me.rerere.tts.provider.providers.JvmSystemTTSProvider

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
    FileKit.init("RikkaHub")
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
            val settingsDataStore = remember(appScope) { createJvmSettingsDataStore(appScope) }
            val settingsStore = remember(appScope, settingsDataStore) {
                SettingsStore(
                    dataStore = settingsDataStore,
                    scope = appScope,
                )
            }
            val webServerRuntime = remember(appScope) {
                createJvmWebServerRuntime(appScope)
            }
            val databaseFile = remember { defaultJvmDatabaseFile() }
            val database = remember(databaseFile) { createJvmAppDatabase(databaseFile) }
            val monitoring = remember { JvmSentryMonitoring() }
            val externalUriOpener = remember { JvmExternalUriOpener() }
            SharedProductApp(
                settingsStore = settingsStore,
                database = database,
                buildInfo = currentDesktopPlatformBuildInfo(),
                externalUriOpener = externalUriOpener,
                webServerRuntime = webServerRuntime,
                booleanPreferenceStore = remember(settingsDataStore) {
                    DataStoreBooleanPreferenceStore(settingsDataStore)
                },
                stringPreferenceStore = remember(settingsDataStore) {
                    DataStoreStringPreferenceStore(settingsDataStore)
                },
                analyticsTracker = monitoring,
                crashReporter = monitoring,
                chatNotificationPresenter = remember { JvmSystemTrayChatNotificationPresenter() },
                systemTtsProvider = remember { JvmSystemTTSProvider() },
                platformAudioPlayer = remember { JvmAudioPlayer() },
                platformRoutes = JvmPlatformRouteContent,
                chatMessagePlatformActions = remember(externalUriOpener) {
                    JvmChatMessagePlatformActions(externalUriOpener)
                },
                richTextPlatformActions = { navigator -> rememberJvmRichTextPlatformActions(navigator) },
                startScreen = if (policy.mode == DesktopLaunchMode.Smoke) Screen.History else null,
                backupFileLayout = remember(databaseFile) {
                    BackupFileLayout.create(PlatformFile(databaseFile))
                },
                oauthCallbackSessionFactory = remember(externalUriOpener) {
                    JvmOAuthCallbackSessionFactory(externalUriOpener)
                },
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
