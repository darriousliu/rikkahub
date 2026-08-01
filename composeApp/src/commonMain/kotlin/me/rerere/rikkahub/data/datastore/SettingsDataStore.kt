package me.rerere.rikkahub.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.CoroutineScope
import me.rerere.rikkahub.data.datastore.migration.PreferenceStoreV1Migration
import me.rerere.rikkahub.data.datastore.migration.PreferenceStoreV2Migration
import me.rerere.rikkahub.data.datastore.migration.PreferenceStoreV3Migration
import okio.Path.Companion.toPath

const val SETTINGS_DATA_STORE_FILE_NAME = "settings.preferences_pb"

fun createSettingsDataStore(
    scope: CoroutineScope,
    producePath: () -> String,
): DataStore<Preferences> = PreferenceDataStoreFactory.createWithPath(
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
    migrations = listOf(
        PreferenceStoreV1Migration(),
        PreferenceStoreV2Migration(),
        PreferenceStoreV3Migration(),
    ),
    scope = scope,
    produceFile = { producePath().toPath() },
)
