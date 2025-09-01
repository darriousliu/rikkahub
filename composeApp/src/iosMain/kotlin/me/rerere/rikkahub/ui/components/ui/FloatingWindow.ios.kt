package me.rerere.rikkahub.ui.components.ui

import androidx.compose.runtime.Composable

@Composable
actual fun FloatingWindow(
    tag: String,
    visibility: Boolean,
    content: @Composable (() -> Unit)
) {
}
