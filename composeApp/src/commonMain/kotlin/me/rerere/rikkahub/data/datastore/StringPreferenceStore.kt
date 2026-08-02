package me.rerere.rikkahub.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

interface StringPreferenceStore {
    suspend fun get(key: String, defaultValue: String? = null): String?

    suspend fun set(key: String, value: String?)
}

class DataStoreStringPreferenceStore(
    private val dataStore: DataStore<Preferences>,
) : StringPreferenceStore {
    override suspend fun get(key: String, defaultValue: String?): String? =
        dataStore.data.recoverFromReadFailure().first()[stringPreferencesKey(key)] ?: defaultValue

    override suspend fun set(key: String, value: String?) {
        dataStore.edit { preferences ->
            val preferenceKey = stringPreferencesKey(key)
            if (value == null) {
                preferences.remove(preferenceKey)
            } else {
                preferences[preferenceKey] = value
            }
        }
    }
}
