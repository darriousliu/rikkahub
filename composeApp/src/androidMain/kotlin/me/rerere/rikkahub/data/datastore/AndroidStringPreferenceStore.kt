package me.rerere.rikkahub.data.datastore

import android.content.Context

class AndroidStringPreferenceStore(
    context: Context,
) : StringPreferenceStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override suspend fun get(key: String, defaultValue: String?): String? =
        preferences.getString(key, defaultValue)

    override suspend fun set(key: String, value: String?) {
        preferences.edit().putString(key, value).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "rikkahub.preferences"
    }
}
