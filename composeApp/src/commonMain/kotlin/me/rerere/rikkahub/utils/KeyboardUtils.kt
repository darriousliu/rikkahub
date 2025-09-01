package me.rerere.rikkahub.utils

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable


expect val WindowInsets.Companion.isImeVisible: Boolean
    @Composable @NonRestartableComposable get
