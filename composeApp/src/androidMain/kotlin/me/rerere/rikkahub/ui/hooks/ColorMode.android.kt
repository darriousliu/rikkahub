package me.rerere.rikkahub.ui.hooks

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

private const val PREFERENCES_NAME = "rikkahub.preferences"
private const val COLOR_MODE_KEY = "colorMode"
private const val AMOLED_DARK_MODE_KEY = "amoledDark"

@Composable
actual fun rememberColorMode(): MutableState<ColorMode> {
    val preferences = LocalContext.current.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    val state = rememberStringPreference(preferences, COLOR_MODE_KEY, ColorMode.SYSTEM.name)
    return remember(state) {
        object : MutableState<ColorMode> {
            override var value: ColorMode
                get() = state.value.toColorMode()
                set(value) {
                    state.value = value.name
                }

            override fun component1(): ColorMode = value
            override fun component2(): (ColorMode) -> Unit = { value = it }
        }
    }
}

@Composable
actual fun rememberCurrentColorMode(): ColorMode = rememberColorMode().value

@Composable
actual fun rememberAmoledDarkMode(): MutableState<Boolean> {
    val preferences = LocalContext.current.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    return rememberBooleanPreference(preferences, AMOLED_DARK_MODE_KEY, false)
}

@Composable
private fun rememberStringPreference(
    preferences: SharedPreferences,
    key: String,
    defaultValue: String,
): MutableState<String> {
    val state = remember(preferences, key) {
        mutableStateOf(preferences.getString(key, defaultValue) ?: defaultValue)
    }
    DisposableEffect(preferences, key) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
            if (changedKey == key) state.value = preferences.getString(key, defaultValue) ?: defaultValue
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return remember(preferences, key, state) {
        object : MutableState<String> {
            override var value: String
                get() = state.value
                set(value) {
                    state.value = value
                    preferences.edit().putString(key, value).apply()
                }

            override fun component1(): String = value
            override fun component2(): (String) -> Unit = { value = it }
        }
    }
}

@Composable
private fun rememberBooleanPreference(
    preferences: SharedPreferences,
    key: String,
    defaultValue: Boolean,
): MutableState<Boolean> {
    val state = remember(preferences, key) {
        mutableStateOf(preferences.getBoolean(key, defaultValue))
    }
    DisposableEffect(preferences, key) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
            if (changedKey == key) state.value = preferences.getBoolean(key, defaultValue)
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return remember(preferences, key, state) {
        object : MutableState<Boolean> {
            override var value: Boolean
                get() = state.value
                set(value) {
                    state.value = value
                    preferences.edit().putBoolean(key, value).apply()
                }

            override fun component1(): Boolean = value
            override fun component2(): (Boolean) -> Unit = { value = it }
        }
    }
}

private fun String?.toColorMode(): ColorMode =
    ColorMode.entries.firstOrNull { it.name == this } ?: ColorMode.SYSTEM
