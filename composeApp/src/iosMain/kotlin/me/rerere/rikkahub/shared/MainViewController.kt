package me.rerere.rikkahub.shared

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.window.ComposeUIViewController
import me.rerere.rikkahub.AppRoutes
import me.rerere.rikkahub.di.createIosAppModule
import me.rerere.rikkahub.ui.theme.RikkahubTheme
import org.koin.compose.KoinApplication
import org.koin.dsl.koinConfiguration
import platform.UIKit.UIViewController

/** UIKit bridge used by the iOS application shell. */
public fun MainViewController(): UIViewController = ComposeUIViewController {
    val appScope = rememberCoroutineScope()
    val configuration = remember(appScope) {
        koinConfiguration { modules(createIosAppModule(appScope)) }
    }
    KoinApplication(configuration = configuration) {
        RikkahubTheme {
            AppRoutes()
        }
    }
}
