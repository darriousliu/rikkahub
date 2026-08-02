package me.rerere.rikkahub.shared

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.window.ComposeUIViewController
import io.github.vinceglb.filekit.PlatformFile
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.DataStoreBooleanPreferenceStore
import me.rerere.rikkahub.data.datastore.DataStoreStringPreferenceStore
import me.rerere.rikkahub.data.datastore.createIosSettingsDataStore
import me.rerere.rikkahub.data.db.createIosAppDatabase
import me.rerere.rikkahub.data.db.defaultIosDatabaseFilePath
import me.rerere.rikkahub.data.sync.BackupFileLayout
import me.rerere.rikkahub.platform.IosExternalUriOpener
import me.rerere.rikkahub.platform.IosFirebaseAnalyticsTracker
import me.rerere.rikkahub.platform.IosFirebaseCrashReporter
import me.rerere.rikkahub.platform.IosOAuthCallbackSessionFactory
import me.rerere.rikkahub.platform.IosUserNotificationPresenter
import me.rerere.rikkahub.web.createIosWebServerRuntime
import me.rerere.tts.controller.IosAudioPlayer
import me.rerere.tts.provider.providers.IosSystemTTSProvider
import platform.UIKit.UIViewController

/** UIKit bridge used by the iOS application shell. */
public fun MainViewController(): UIViewController = ComposeUIViewController {
    val appScope = rememberCoroutineScope()
    val settingsDataStore = remember(appScope) { createIosSettingsDataStore(appScope) }
    val settingsStore = remember(appScope, settingsDataStore) {
        SettingsStore(
            dataStore = settingsDataStore,
            scope = appScope,
        )
    }
    val webServerRuntime = remember(appScope) {
        createIosWebServerRuntime(appScope)
    }
    val database = remember { createIosAppDatabase() }
    SharedProductApp(
        settingsStore = settingsStore,
        database = database,
        buildInfo = currentIosPlatformBuildInfo(),
        externalUriOpener = IosExternalUriOpener(),
        webServerRuntime = webServerRuntime,
        booleanPreferenceStore = remember(settingsDataStore) {
            DataStoreBooleanPreferenceStore(settingsDataStore)
        },
        stringPreferenceStore = remember(settingsDataStore) {
            DataStoreStringPreferenceStore(settingsDataStore)
        },
        analyticsTracker = remember { IosFirebaseAnalyticsTracker() },
        crashReporter = remember { IosFirebaseCrashReporter() },
        chatNotificationPresenter = remember { IosUserNotificationPresenter() },
        systemTtsProvider = remember { IosSystemTTSProvider() },
        platformAudioPlayer = remember { IosAudioPlayer() },
        backupFileLayout = remember {
            BackupFileLayout.create(PlatformFile(defaultIosDatabaseFilePath()))
        },
        oauthCallbackSessionFactory = remember { IosOAuthCallbackSessionFactory() },
    )
}
