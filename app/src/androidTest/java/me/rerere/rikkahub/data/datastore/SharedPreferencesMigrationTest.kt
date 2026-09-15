package me.rerere.rikkahub.data.datastore

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedPreferencesMigrationTest {
    @Test
    fun migratesLegacyValuesAndUsesDataStoreAfterRestart(): Unit = runBlocking {
        val context = IsolatedContext()
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val legacy = context.getSharedPreferences("rikkahub.preferences", Context.MODE_PRIVATE)
            assertTrue(legacy.edit()
                .putBoolean("create_new_conversation_on_start", false)
                .putString("lastConversationId", "7ca7c832-ff2f-408b-aac8-191ad7e5a806")
                .putString("colorMode", "DARK")
                .putBoolean("amoledDark", true)
                .putString("search_history", "[\"中文\",\"pinyin\"]")
                .commit())
            val dataStore = createAndroidSettingsDataStore(context, scope)
            val booleans = DataStoreBooleanPreferenceStore(dataStore)
            val strings = DataStoreStringPreferenceStore(dataStore)
            assertFalse(booleans.observe("create_new_conversation_on_start", true).first())
            assertTrue(booleans.observe("amoledDark", false).first())
            assertEquals("DARK", strings.get("colorMode"))
            assertEquals("7ca7c832-ff2f-408b-aac8-191ad7e5a806", strings.get("lastConversationId"))
            assertEquals("[\"中文\",\"pinyin\"]", strings.get("search_history"))
            assertTrue(legacy.all.isEmpty())

            booleans.set("create_new_conversation_on_start", true)
            strings.set("colorMode", "LIGHT")
            strings.set("lastConversationId", null)
            scope.coroutineContext.job.cancelAndJoin()

            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val reopened = createAndroidSettingsDataStore(context, scope)
            assertTrue(DataStoreBooleanPreferenceStore(reopened)
                .observe("create_new_conversation_on_start", false).first())
            assertEquals("LIGHT", DataStoreStringPreferenceStore(reopened).get("colorMode"))
            assertNull(DataStoreStringPreferenceStore(reopened).get("lastConversationId"))
            assertTrue(context.getSharedPreferences("rikkahub.preferences", Context.MODE_PRIVATE).all.isEmpty())
        } finally {
            scope.coroutineContext.job.cancelAndJoin()
            context.cleanup()
        }
    }

    @Test
    fun existingDataStoreValuesTakePriorityAndMissingKeysStillMigrate(): Unit = runBlocking {
        val context = IsolatedContext()
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val existing = createSettingsDataStore(scope) {
                context.preferencesDataStoreFile("settings").absolutePath
            }
            existing.edit {
                it[stringPreferencesKey("colorMode")] = "LIGHT"
                it[SettingsStore.DYNAMIC_COLOR] = false
            }
            scope.coroutineContext.job.cancelAndJoin()
            val legacy = context.getSharedPreferences("rikkahub.preferences", Context.MODE_PRIVATE)
            assertTrue(legacy.edit()
                .putString("colorMode", "DARK")
                .putBoolean("amoledDark", true)
                .commit())

            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val migrated = createAndroidSettingsDataStore(context, scope)
            assertEquals("LIGHT", DataStoreStringPreferenceStore(migrated).get("colorMode"))
            assertTrue(DataStoreBooleanPreferenceStore(migrated).observe("amoledDark", false).first())
            assertEquals(false, migrated.data.first()[SettingsStore.DYNAMIC_COLOR])
            assertTrue(legacy.all.isEmpty())
        } finally {
            scope.coroutineContext.job.cancelAndJoin()
            context.cleanup()
        }
    }

    private class IsolatedContext : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
        private val prefix = "di-migration-${UUID.randomUUID()}"
        private val root = File(baseContext.cacheDir, prefix).apply { mkdirs() }

        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = root
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            baseContext.getSharedPreferences("$prefix.$name", mode)
        override fun deleteSharedPreferences(name: String): Boolean =
            baseContext.deleteSharedPreferences("$prefix.$name")

        fun cleanup() {
            deleteSharedPreferences("rikkahub.preferences")
            root.deleteRecursively()
        }
    }
}
