package me.rerere.rikkahub.platform

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) = Unit
