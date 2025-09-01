package me.rerere.rikkahub.utils

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.ui.platform.LocalDensity

actual val WindowInsets.Companion.isImeVisible: Boolean
    @NonRestartableComposable
    @Composable
    get() = WindowInsets.ime.getBottom(LocalDensity.current) > 0
