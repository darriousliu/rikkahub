package me.rerere.rikkahub.ui.components.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.material3.MaterialTheme
import me.rerere.rikkahub.platform.createFloatingWindowHost

@Composable
fun FloatingWindow(
    tag: String,
    visibility: Boolean = true,
    content: @Composable () -> Unit,
) {
    val host = remember { createFloatingWindowHost() }
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val shapes = MaterialTheme.shapes
    host.Content(
        tag = tag,
        visible = visibility,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = shapes,
        ) {
            content()
        }
    }
}
