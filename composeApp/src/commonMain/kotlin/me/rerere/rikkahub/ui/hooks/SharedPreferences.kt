package me.rerere.rikkahub.ui.hooks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import me.rerere.common.PlatformContext

@Composable
expect fun rememberSharedPreferenceString(
    keyForString: String,
    defaultValue: String? = null
): MutableState<String?>

@Composable
expect fun rememberSharedPreferenceBoolean(
    keyForBoolean: String,
    defaultValue: Boolean = false
): MutableState<Boolean>

expect fun PlatformContext.writeStringPreference(
    key: String,
    value: String?
)

expect fun PlatformContext.readStringPreference(
    key: String,
    defaultValue: String? = null
): String?

expect fun PlatformContext.writeBooleanPreference(
    key: String,
    value: Boolean
)

expect fun PlatformContext.readBooleanPreference(
    key: String,
    defaultValue: Boolean = false
): Boolean
