package me.rerere.rikkahub.ui.hooks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.datastore.recoverFromReadFailure
import org.koin.compose.koinInject

@Serializable
enum class ColorMode {
    SYSTEM,
    LIGHT,
    DARK,
}

@Composable
fun rememberColorMode(): MutableState<ColorMode> {
    val dataStore = koinInject<DataStore<Preferences>>()
    val scope = koinInject<AppScope>()
    val key = remember { stringPreferencesKey("colorMode") }
    val values = remember(dataStore) {
        dataStore.data.recoverFromReadFailure().map { preferences ->
            ColorMode.entries.firstOrNull { it.name == preferences[key] } ?: ColorMode.SYSTEM
        }.distinctUntilChanged()
    }
    val mode by values.collectAsStateWithLifecycle(ColorMode.SYSTEM)
    val currentValue = rememberUpdatedState(mode)
    return remember(dataStore, scope) {
        object : MutableState<ColorMode> {
            override var value: ColorMode
                get() = currentValue.value
                set(value) {
                    scope.launch { dataStore.edit { it[key] = value.name } }
                }

            override fun component1(): ColorMode = value
            override fun component2(): (ColorMode) -> Unit = { value = it }
        }
    }
}

@Composable
fun rememberCurrentColorMode(): ColorMode = rememberColorMode().value

@Composable
fun rememberAmoledDarkMode(): MutableState<Boolean> = rememberSharedPreferenceBoolean("amoledDark", false)
