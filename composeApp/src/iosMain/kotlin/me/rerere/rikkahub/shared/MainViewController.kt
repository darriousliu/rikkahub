package me.rerere.rikkahub.shared

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/** UIKit bridge used by the iOS application shell. */
public fun MainViewController(): UIViewController = ComposeUIViewController {
    RikkaHubApp()
}
