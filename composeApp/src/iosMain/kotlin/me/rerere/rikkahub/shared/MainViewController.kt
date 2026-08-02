package me.rerere.rikkahub.shared

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.window.ComposeUIViewController
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.DataStoreBooleanPreferenceStore
import me.rerere.rikkahub.data.datastore.DataStoreStringPreferenceStore
import me.rerere.rikkahub.data.datastore.createIosSettingsDataStore
import me.rerere.rikkahub.data.db.createIosAppDatabase
import me.rerere.rikkahub.platform.IosExternalUriOpener
import me.rerere.rikkahub.web.createIosWebServerRuntime
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
    )
}
