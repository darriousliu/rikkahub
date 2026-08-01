package me.rerere.rikkahub.platform

import androidx.compose.runtime.Composable

public actual fun createFloatingWindowHost(): FloatingWindowHost = NoOpFloatingWindowHost

private data object NoOpFloatingWindowHost : FloatingWindowHost {
    @Composable
    override fun Content(
        tag: String,
        visible: Boolean,
        content: @Composable () -> Unit,
    ) = Unit
}
