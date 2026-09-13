package me.rerere.rikkahub.ui.hooks

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable

actual val WindowInsets.Companion.isImeVisible: Boolean
    @Composable get() = false
