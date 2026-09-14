package me.rerere.rikkahub.service

import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.files.SkillManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.vinceglb.filekit.PlatformFile
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.io.files.Path
import me.rerere.ai.provider.Provider
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.ai.GenerationHandler
import me.rerere.rikkahub.data.ai.tools.local.LocalTools
import me.rerere.rikkahub.data.datastore.BooleanPreferenceStore
import me.rerere.rikkahub.data.datastore.DataStoreBooleanPreferenceStore
import me.rerere.rikkahub.data.datastore.DataStoreStringPreferenceStore
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.StringPreferenceStore
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.AppDatabaseConstructor
import me.rerere.rikkahub.data.db.buildAppDatabase
import me.rerere.rikkahub.data.db.fts.MessageFtsDialect
import me.rerere.rikkahub.data.db.fts.MessageFtsManager
import me.rerere.rikkahub.data.event.AppEventBus
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.di.appModule
import me.rerere.rikkahub.di.dataSourceModule
import me.rerere.rikkahub.di.repositoryModule
import me.rerere.rikkahub.platform.OAuthCallbackSessionFactory
import me.rerere.rikkahub.shared.template.createMessageTemplateEngine
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.nio.file.Files

/** Real common DI, SQLite and service; only platform I/O and the provider are test doubles. */
internal class ChatServiceTestFixture(
    provider: Provider<ProviderSetting.OpenAI>? = null,
    existingDatabase: AppDatabase? = null,
    existingRepository: ConversationRepository? = null,
) : AutoCloseable {
    private val root = Files.createTempDirectory("chat-service-test-").toFile()
    private val ownsDatabase = existingDatabase == null
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val preferences = object : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(data.value).also { data.value = it }
    }
    val settings = SettingsStore(preferences, scope)
    val database = existingDatabase ?: buildAppDatabase(
        Room.inMemoryDatabaseBuilder<AppDatabase>(AppDatabaseConstructor::initialize),
        BundledSQLiteDriver(), MessageFtsDialect.UNICODE61,
    )
    val fts = MessageFtsManager(database, MessageFtsDialect.UNICODE61)
    private val client = HttpClient(MockEngine { error("Unexpected network request") })
    val providers = ProviderManager(client).apply { provider?.let { registerProvider("openai", it) } }
    private val application = koinApplication {
        modules(appModule, dataSourceModule, repositoryModule, module {
            single<CoroutineScope> { scope }
            single { settings }
            single { database }
            single { providers }
            single { AppEventBus() }
            single { createMessageTemplateEngine() }
            single<BooleanPreferenceStore> { DataStoreBooleanPreferenceStore(preferences) }
            single<StringPreferenceStore> { DataStoreStringPreferenceStore(preferences) }
            single { fts }
            single { FilesManager(Path(root.path), get(), scope, asyncFileIo = true) }
            single { SkillManager(Path(root.path), get()) }
            single { GenerationHandler(Path(root.path), get(), get(), get()) }
            single<OAuthCallbackSessionFactory> { OAuthCallbackSessionFactory { error("Unexpected OAuth") } }
            single { LocalTools(get(), settings, null) }
            existingRepository?.let { repository -> single { repository } }
        })
    }
    val repository: ConversationRepository = application.koin.get()
    val service: ChatService = application.koin.get()
    val generationHandler: GenerationHandler = application.koin.get()

    suspend fun configure(value: Settings) = settings.update(value.copy(init = false))

    suspend fun load(conversation: Conversation) {
        repository.insertConversation(conversation)
        service.updateConversationState(conversation.id) { conversation }
    }

    suspend fun awaitLaunched(block: () -> Unit) {
        val existing = scope.coroutineContext.job.children.toSet()
        block()
        scope.coroutineContext.job.children.filter { it !in existing }.toList().joinAll()
    }

    override fun close() {
        scope.cancel()
        application.close()
        client.close()
        if (ownsDatabase) database.close()
        root.deleteRecursively()
    }
}
