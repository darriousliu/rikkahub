package me.rerere.rikkahub.data.datastore

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

class AndroidBooleanPreferenceStore(
    context: Context,
) : BooleanPreferenceStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun observe(key: String, defaultValue: Boolean): Flow<Boolean> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, changedKey ->
            if (changedKey == key) {
                trySend(sharedPreferences.getBoolean(key, defaultValue))
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        trySend(preferences.getBoolean(key, defaultValue))
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    override suspend fun set(key: String, value: Boolean) {
        preferences.edit().putBoolean(key, value).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "rikkahub.preferences"
    }
}
