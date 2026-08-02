package me.rerere.rikkahub.ui.hooks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import kotlinx.serialization.Serializable

@Serializable
enum class ColorMode {
    SYSTEM,
    LIGHT,
    DARK,
}

@Composable
expect fun rememberColorMode(): MutableState<ColorMode>

@Composable
expect fun rememberCurrentColorMode(): ColorMode

@Composable
expect fun rememberAmoledDarkMode(): MutableState<Boolean>
