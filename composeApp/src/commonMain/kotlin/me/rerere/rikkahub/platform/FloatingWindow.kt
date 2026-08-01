package me.rerere.rikkahub.platform

import androidx.compose.runtime.Composable

public interface FloatingWindowHost {
    @Composable
    public fun Content(
        tag: String,
        visible: Boolean,
        content: @Composable () -> Unit,
    )
}

public expect fun createFloatingWindowHost(): FloatingWindowHost
