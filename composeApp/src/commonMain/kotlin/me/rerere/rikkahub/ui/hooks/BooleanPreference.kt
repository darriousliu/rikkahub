package me.rerere.rikkahub.ui.hooks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.datastore.BooleanPreferenceStore
import org.koin.compose.koinInject

@Composable
fun rememberSharedPreferenceBoolean(
    keyForBoolean: String,
    defaultValue: Boolean = false,
): MutableState<Boolean> {
    val store: BooleanPreferenceStore = koinInject()
    val scope = rememberCoroutineScope()
    val values = remember(store, keyForBoolean, defaultValue) {
        store.observe(keyForBoolean, defaultValue)
    }
    val value by values.collectAsStateWithLifecycle(defaultValue)
    val currentValue = rememberUpdatedState(value)
    return remember(store, scope, keyForBoolean) {
        object : MutableState<Boolean> {
            override var value: Boolean
                get() = currentValue.value
                set(value) {
                    scope.launch { store.set(keyForBoolean, value) }
            }

            override fun component1(): Boolean = value
            override fun component2(): (Boolean) -> Unit = { newValue ->
                scope.launch { store.set(keyForBoolean, newValue) }
            }
        }
    }
}
