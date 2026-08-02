package me.rerere.rikkahub.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

@Composable
internal expect fun platformDynamicColorScheme(enabled: Boolean, darkTheme: Boolean): ColorScheme?

@Composable
internal expect fun PlatformSystemBarsEffect(darkTheme: Boolean)
