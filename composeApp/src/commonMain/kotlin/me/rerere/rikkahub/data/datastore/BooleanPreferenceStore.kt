package me.rerere.rikkahub.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

interface BooleanPreferenceStore {
    fun observe(key: String, defaultValue: Boolean): Flow<Boolean>

    suspend fun set(key: String, value: Boolean)
}

class DataStoreBooleanPreferenceStore(
    private val dataStore: DataStore<Preferences>,
) : BooleanPreferenceStore {
    override fun observe(key: String, defaultValue: Boolean): Flow<Boolean> {
        val preferenceKey = booleanPreferencesKey(key)
        return dataStore.data
            .recoverFromReadFailure()
            .map { preferences -> preferences[preferenceKey] ?: defaultValue }
            .distinctUntilChanged()
    }

    override suspend fun set(key: String, value: Boolean) {
        dataStore.edit { preferences ->
            preferences[booleanPreferencesKey(key)] = value
        }
    }
}
