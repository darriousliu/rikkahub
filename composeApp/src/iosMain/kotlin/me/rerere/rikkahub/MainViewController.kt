package me.rerere.rikkahub

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.window.ComposeUIViewController
import androidx.navigation.compose.rememberNavController
import me.rerere.rikkahub.utils.IosHapticFeedback

fun MainViewController() = ComposeUIViewController {
    val navigationController = rememberNavController().also {
        NavigationController.navHostController = it
    }
    CompositionLocalProvider(LocalHapticFeedback provides IosHapticFeedback) {
        App(navBackStack = navigationController)
    }
}
