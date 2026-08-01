package me.rerere.rikkahub.ui.components.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import me.rerere.rikkahub.platform.createFloatingWindowHost
import me.rerere.rikkahub.ui.theme.RikkahubTheme

@Composable
fun FloatingWindow(
    tag: String,
    visibility: Boolean = true,
    content: @Composable () -> Unit,
) {
    val host = remember { createFloatingWindowHost() }
    host.Content(
        tag = tag,
        visible = visibility,
    ) {
        RikkahubTheme {
            content()
        }
    }
}
