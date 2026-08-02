package me.rerere.rikkahub.shared

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.window.ComposeUIViewController
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.createIosSettingsDataStore
import me.rerere.rikkahub.platform.IosExternalUriOpener
import platform.UIKit.UIViewController

/** UIKit bridge used by the iOS application shell. */
public fun MainViewController(): UIViewController = ComposeUIViewController {
    val appScope = rememberCoroutineScope()
    val settingsStore = remember(appScope) {
        SettingsStore(
            dataStore = createIosSettingsDataStore(appScope),
            scope = appScope,
        )
    }
    SharedProductApp(
        settingsStore = settingsStore,
        buildInfo = currentIosPlatformBuildInfo(),
        externalUriOpener = IosExternalUriOpener(),
    )
}
