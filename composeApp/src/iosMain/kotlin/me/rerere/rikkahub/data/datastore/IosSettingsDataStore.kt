package me.rerere.rikkahub.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import platform.Foundation.NSFileManager
import platform.Foundation.NSHomeDirectory

@OptIn(ExperimentalForeignApi::class)
fun createIosSettingsDataStore(
    scope: CoroutineScope,
    directory: String = "${NSHomeDirectory()}/Library/Application Support/RikkaHub/datastore",
): DataStore<Preferences> {
    NSFileManager.defaultManager.createDirectoryAtPath(
        path = directory,
        withIntermediateDirectories = true,
        attributes = null,
        error = null,
    )
    return createSettingsDataStore(scope) {
        "$directory/$SETTINGS_DATA_STORE_FILE_NAME"
    }
}
