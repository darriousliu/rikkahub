package me.rerere.rikkahub.ui.hooks

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.platform.LocalDensity

actual val WindowInsets.Companion.isImeVisible: Boolean
    @Composable get() = WindowInsets.ime.getBottom(LocalDensity.current) > 0
