package me.rerere.rikkahub.ui.components.ui

import androidx.compose.runtime.staticCompositionLocalOf

val LocalExportContext = staticCompositionLocalOf { false }

val LocalImageSaveHandler = staticCompositionLocalOf<(suspend (String) -> Unit)?> { null }
