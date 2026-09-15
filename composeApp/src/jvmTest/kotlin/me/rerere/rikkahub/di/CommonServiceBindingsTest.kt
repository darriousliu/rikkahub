package me.rerere.rikkahub.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.cacheDir
import io.github.vinceglb.filekit.databasesDir
import io.github.vinceglb.filekit.filesDir
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.util.KeyRoulette
import me.rerere.rikkahub.data.datastore.BooleanPreferenceStore
import me.rerere.rikkahub.data.datastore.DEFAULT_PROVIDERS
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.StringPreferenceStore
import me.rerere.rikkahub.data.db.createJvmAppDatabase
import me.rerere.rikkahub.data.db.entity.ManagedFileEntity
import me.rerere.rikkahub.data.sync.BackupFileLayout
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CommonServiceBindingsTest {
    @Test
    fun defaultDatabasePersistsInFileKitDatabasesDirectory() = runTest {
        val root = Files.createTempDirectory("database-path-").toFile()
        FileKit.init(filesDir = root.resolve("files"), cacheDir = root.resolve("cache"))
        try {
            val original = ManagedFileEntity(
                folder = "upload", relativePath = "upload/note.txt", displayName = "note.txt",
                mimeType = "text/plain", sizeBytes = 4, createdAt = 1, updatedAt = 1,
            )
            val database = createJvmAppDatabase()
            val id = try {
                database.managedFileDao().insert(original)
            } finally {
                database.close()
            }
            assertTrue(root.resolve("files/databases/rikka_hub").isFile)
            val reopened = createJvmAppDatabase()
            try {
                assertEquals(original.copy(id = id), reopened.managedFileDao().getById(id))
            } finally {
                reopened.close()
            }
        } finally {
            FileKit.init("RikkaHub")
            root.deleteRecursively()
        }
    }

    @Test
    fun settingsAndPreferenceStoresShareOneDataStore() = runTest {
        val dataStore = object : DataStore<Preferences> {
            override val data = MutableStateFlow(emptyPreferences())
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                transform(data.value).also { data.value = it }
        }
        val application = koinApplication(createEagerInstances = false) {
            modules(commonModule, module {
                single<DataStore<Preferences>> { dataStore }
                single<CoroutineScope> { backgroundScope }
            })
        }
        try {
            val settings = application.koin.get<SettingsStore>()
            application.koin.get<BooleanPreferenceStore>().set("dynamic_color", false)
            application.koin.get<StringPreferenceStore>().set("theme_id", "purple")
            val current = settings.settingsFlowRaw.first()
            assertFalse(current.dynamicColor)
            assertEquals("purple", current.themeId)
            DEFAULT_PROVIDERS.forEach { original ->
                assertSame(original.description, current.providers.first { it.id == original.id }.description)
            }
            assertSame(settings, application.koin.get<SettingsStore>())
        } finally {
            application.close()
        }
    }

    @Test
    fun providerUsesThePlatformAiClientWhenPresentAndOtherwiseTheCommonClient() = runTest {
        for (separateAiClient in listOf(false, true)) {
            fun client(label: String) = HttpClient(MockEngine {
                assertEquals("Bearer test", it.headers[HttpHeaders.Authorization])
                assertEquals("/v1/models", it.url.encodedPath)
                respond("""{"data":[{"id":"$label"}]}""", headers = headersOf("Content-Type", "application/json"))
            })
            val general = client("general")
            val ai = client("ai")
            val application = koinApplication(createEagerInstances = false) {
                modules(commonModule, module {
                    single { general }
                    single<KeyRoulette> { KeyRoulette.default() }
                    if (separateAiClient) single<HttpClient>(named("ai")) { ai }
                })
            }
            try {
                val setting = ProviderSetting.OpenAI(baseUrl = "https://example.test/v1", apiKey = "test")
                val provider = application.koin.get<ProviderManager>().getProviderByType(setting)
                assertEquals(listOf(if (separateAiClient) "ai" else "general"),
                    provider.listModels(setting).map { it.modelId })
            } finally {
                application.close()
                general.close()
                ai.close()
            }
        }
    }

    @Test
    fun backupUsesFileKitDatabasesDirectoryAndStableArchiveNames() {
        FileKit.init("RikkaHub")
        val application = koinApplication(createEagerInstances = false) { modules(commonModule) }
        try {
            val layout = application.koin.get<BackupFileLayout>()
            val database = FileKit.databasesDir.file.resolve("rikka_hub")
            assertEquals(FileKit.filesDir, layout.filesRoot)
            assertEquals(FileKit.cacheDir, layout.cacheRoot)
            assertEquals(mapOf(
                "rikka_hub.db" to database,
                "rikka_hub-wal" to database.resolveSibling("rikka_hub-wal"),
                "rikka_hub-shm" to database.resolveSibling("rikka_hub-shm"),
            ), layout.databaseFiles.mapValues { it.value.file })
        } finally {
            application.close()
        }
    }

    @Test
    @OptIn(KoinInternalApi::class)
    fun platformBindingsDoNotOverrideCommonBindings() {
        assertTrue(commonModule.mappings.keys.intersect(jvmModule.mappings.keys).isEmpty())
    }
}
