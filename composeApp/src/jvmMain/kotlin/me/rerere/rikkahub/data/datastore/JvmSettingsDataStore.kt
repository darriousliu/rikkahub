package me.rerere.rikkahub.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import java.io.File

fun createJvmSettingsDataStore(
    scope: CoroutineScope,
    directory: File = File(System.getProperty("user.home"), ".rikkahub/datastore"),
): DataStore<Preferences> {
    directory.mkdirs()
    return createSettingsDataStore(scope) {
        directory.resolve(SETTINGS_DATA_STORE_FILE_NAME).absolutePath
    }
}
