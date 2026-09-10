package me.rerere.rikkahub.data.files

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.io.files.Path
import kotlinx.io.files.SystemTemporaryDirectory
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.utils.canonicalFile
import me.rerere.rikkahub.utils.deleteRecursively
import me.rerere.rikkahub.utils.mkdirs
import kotlin.uuid.Uuid

internal class SkillTestFixture {
    val root = Path(SystemTemporaryDirectory, "cmp-skill-test-${Uuid.random()}").canonicalFile.apply { mkdirs() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val preferences = object : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(data.value).also { data.value = it }
    }
    val settings = SettingsStore(preferences, scope)
    val manager = SkillManager(root, settings)

    fun close() {
        scope.cancel()
        root.deleteRecursively()
    }
}
