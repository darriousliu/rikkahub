package me.rerere.rikkahub.data.datastore

import androidx.datastore.preferences.preferencesDataStore
import me.rerere.common.PlatformContext
import me.rerere.rikkahub.data.datastore.migration.PreferenceStoreV1Migration

internal actual val PlatformContext.settingsStore by preferencesDataStore(
    name = "settings",
    produceMigrations = { context ->
        listOf(
            PreferenceStoreV1Migration()
        )
    }
)
