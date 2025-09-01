package me.rerere.rikkahub.utils

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable

@OptIn(ExperimentalLayoutApi::class)
actual val WindowInsets.Companion.isImeVisible: Boolean
    @NonRestartableComposable
    @Composable
    get() = this.isImeVisible
