package me.rerere.rikkahub.data.datastore

import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.preferencesDataStore
import me.rerere.common.PlatformContext

internal actual val PlatformContext.settingsStore by preferencesDataStore(
    name = "settings",
    produceMigrations = { context ->
        listOf(
            SharedPreferencesMigration(context, "settings"),
        )
    }
)
