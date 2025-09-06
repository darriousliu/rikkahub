package me.rerere.rikkahub.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import me.rerere.common.PlatformContext

internal actual val PlatformContext.settingsStore: DataStore<Preferences>
    get() = TODO("Not yet implemented")
