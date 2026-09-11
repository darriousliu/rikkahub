package me.rerere.rikkahub.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.filesDir
import io.github.vinceglb.filekit.toKotlinxIoPath
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import me.rerere.rikkahub.data.files.LegacyIosFileMigration
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
    return createSettingsDataStore(scope, listOf(LegacyIosFileMigration(FileKit.filesDir.toKotlinxIoPath()))) {
        "$directory/$SETTINGS_DATA_STORE_FILE_NAME"
    }
}
