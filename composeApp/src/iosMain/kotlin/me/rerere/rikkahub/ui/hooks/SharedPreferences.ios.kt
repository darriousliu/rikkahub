package me.rerere.rikkahub.ui.hooks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import me.rerere.common.PlatformContext

@Composable
actual fun rememberSharedPreferenceString(
    keyForString: String,
    defaultValue: String?
): MutableState<String?> {
    TODO("Not yet implemented")
}

@Composable
actual fun rememberSharedPreferenceBoolean(
    keyForBoolean: String,
    defaultValue: Boolean
): MutableState<Boolean> {
    TODO("Not yet implemented")
}

actual fun PlatformContext.writeStringPreference(key: String, value: String?) {
}

actual fun PlatformContext.readStringPreference(
    key: String,
    defaultValue: String?
): String? {
    TODO("Not yet implemented")
}

actual fun PlatformContext.writeBooleanPreference(key: String, value: Boolean) {
}

actual fun PlatformContext.readBooleanPreference(
    key: String,
    defaultValue: Boolean
): Boolean {
    TODO("Not yet implemented")
}
