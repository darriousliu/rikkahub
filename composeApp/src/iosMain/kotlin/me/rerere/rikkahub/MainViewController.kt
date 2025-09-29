package me.rerere.rikkahub

import androidx.compose.ui.window.ComposeUIViewController
import androidx.navigation.compose.rememberNavController

fun MainViewController() = ComposeUIViewController {
    val navigationController = rememberNavController().also {
        NavigationController.navHostController = it
    }
    App(navBackStack = navigationController)
}
