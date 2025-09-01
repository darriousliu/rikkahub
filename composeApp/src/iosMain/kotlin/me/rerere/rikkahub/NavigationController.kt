package me.rerere.rikkahub

import androidx.navigation.NavHostController

object NavigationController {
    internal var navHostController: NavHostController? = null

    fun navigateToChat(conversationId: String) {
        navHostController?.navigate(Screen.Chat(conversationId))
    }
}
