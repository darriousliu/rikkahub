package me.rerere.rikkahub.ui.hooks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

@Composable
actual fun rememberColorMode(): MutableState<ColorMode> = remember { mutableStateOf(ColorMode.SYSTEM) }

@Composable
actual fun rememberCurrentColorMode(): ColorMode = rememberColorMode().value

@Composable
actual fun rememberAmoledDarkMode(): MutableState<Boolean> = remember { mutableStateOf(false) }
