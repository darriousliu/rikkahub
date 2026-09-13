package me.rerere.rikkahub.ui.components.ui

import androidx.compose.runtime.Composable
import me.rerere.rikkahub.shared.PlatformBuildInfo
import me.rerere.rikkahub.ui.pages.chat.ChatVM

@Composable
expect fun UpdateCard(vm: ChatVM, buildInfo: PlatformBuildInfo)
