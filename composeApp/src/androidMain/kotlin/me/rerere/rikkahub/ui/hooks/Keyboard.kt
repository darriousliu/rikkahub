package me.rerere.rikkahub.ui.hooks

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.isImeVisible as androidImeVisible

actual val WindowInsets.Companion.isImeVisible: Boolean
    @Composable get() = WindowInsets.androidImeVisible
