package me.rerere.rikkahub.ui.pages.share.handler

import androidx.compose.runtime.Composable
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.UnavailableRoute

@Composable
actual fun ShareHandlerPage(text: String, image: String?) = UnavailableRoute(Screen.ShareHandler(text, image))
