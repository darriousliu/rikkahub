package me.rerere.rikkahub.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.CoroutineScope

fun createAndroidSettingsDataStore(
    context: Context,
    scope: CoroutineScope,
): DataStore<Preferences> = createSettingsDataStore(scope) {
    context.applicationContext.preferencesDataStoreFile("settings").absolutePath
}
