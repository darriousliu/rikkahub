package me.rerere.rikkahub.ui.hooks

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable

expect val WindowInsets.Companion.isImeVisible: Boolean
    @Composable get
