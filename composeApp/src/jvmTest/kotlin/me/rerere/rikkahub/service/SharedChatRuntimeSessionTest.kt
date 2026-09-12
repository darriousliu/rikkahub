package me.rerere.rikkahub.service

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.vinceglb.filekit.PlatformFile
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import me.rerere.ai.provider.EmbeddingGenerationParams
import me.rerere.ai.provider.EmbeddingGenerationResult
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.Provider
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.MessageChunk
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageChoice
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.mcp.McpImageStore
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.ai.tools.local.LocalTools
import me.rerere.rikkahub.data.ai.transformers.AssistantTemplateLoader
import me.rerere.rikkahub.data.ai.transformers.Base64ImageStore
import me.rerere.rikkahub.data.ai.transformers.DocumentTextExtractor
import me.rerere.rikkahub.data.ai.transformers.TemplateTransformer
import me.rerere.rikkahub.data.datastore.DataStoreBooleanPreferenceStore
import me.rerere.rikkahub.data.datastore.DataStoreStringPreferenceStore
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.AppDatabaseConstructor
import me.rerere.rikkahub.data.db.buildAppDatabase
import me.rerere.rikkahub.data.db.fts.MessageFtsDialect
import me.rerere.rikkahub.data.db.fts.MessageFtsManager
import me.rerere.rikkahub.data.event.AppEventBus
import me.rerere.rikkahub.data.files.SkillManager
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.FolderRepository
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.platform.FileKitFileCleaner
import me.rerere.rikkahub.platform.FileKitPlatformFileStore
import me.rerere.rikkahub.platform.OAuthCallbackSessionFactory
import me.rerere.rikkahub.shared.template.createMessageTemplateEngine
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

class SharedChatRuntimeSessionTest {
    @Test
    fun `only the final release evicts state after five seconds`() = sessionTest { f ->
        val id = Uuid.random()
        f.runtime.addConversationReference(id)
        f.runtime.addConversationReference(id)
        val state = f.runtime.getConversationFlow(id)
        f.runtime.updateConversationState(id) { it.copy(title = "in memory") }
        f.runtime.removeConversationReference(id)
        advanceTimeBy(10_000)
        runCurrent()
        assertSame(state, f.runtime.getConversationFlow(id))
        f.runtime.removeConversationReference(id)
        runCurrent()
        advanceTimeBy(4_999)
        runCurrent()
        assertSame(state, f.runtime.getConversationFlow(id))
        advanceTimeBy(1)
        runCurrent()
        assertNotSame(state, f.runtime.getConversationFlow(id))
        assertEquals("", f.runtime.getConversationFlow(id).value.title)
    }

    @Test
    fun `returning before timeout retains state and restarts the next idle delay`() = sessionTest { f ->
        val id = Uuid.random()
        f.runtime.addConversationReference(id)
        val state = f.runtime.getConversationFlow(id)
        f.runtime.removeConversationReference(id)
        runCurrent()
        advanceTimeBy(4_000)
        f.runtime.addConversationReference(id)
        advanceTimeBy(6_000)
        runCurrent()
        assertSame(state, f.runtime.getConversationFlow(id))
        f.runtime.removeConversationReference(id)
        runCurrent()
        advanceTimeBy(4_999)
        runCurrent()
        assertSame(state, f.runtime.getConversationFlow(id))
        advanceTimeBy(1)
        runCurrent()
        assertNotSame(state, f.runtime.getConversationFlow(id))
    }

    @Test
    fun `missing status reads do not retain entries across session creation or eviction`() = sessionTest { f ->
        val id = Uuid.random()
        val missing = f.runtime.getProcessingStatusFlow(id)
        assertNull(f.runtime.getGenerationJobStateFlow(id).first())
        assertEquals(emptyMap(), f.runtime.getConversationJobs().first())
        f.runtime.removeConversationReference(id)
        f.runtime.addConversationReference(id)
        val loaded = f.runtime.getProcessingStatusFlow(id)
        assertNotSame(missing, loaded)
        assertSame(loaded, f.runtime.getProcessingStatusFlow(id))
        f.runtime.removeConversationReference(id)
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()
        assertNotSame(loaded, f.runtime.getProcessingStatusFlow(id))
    }

    @Test
    fun `eviction drops only memory and initialize reloads the saved conversation`() = sessionTest { f ->
        val id = Uuid.random()
        f.runtime.addConversationReference(id)
        f.runtime.initializeConversation(id)
        val state = f.runtime.getConversationFlow(id)
        f.runtime.saveConversation(id, state.value.copy(title = "persisted"))
        f.runtime.updateConversationState(id) { it.copy(title = "unsaved") }
        f.runtime.removeConversationReference(id)
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()
        f.runtime.addConversationReference(id)
        val reloaded = f.runtime.getConversationFlow(id)
        assertNotSame(state, reloaded)
        f.runtime.initializeConversation(id)
        assertEquals("persisted", reloaded.value.title)
        assertEquals("persisted", f.repository.getConversationById(id)?.title)
    }

    @Test
    fun `active generation survives release and completion updates job observers before idle eviction`() = sessionTest { f ->
        val id = f.initialize()
        val state = f.runtime.getConversationFlow(id)
        val observed = mutableListOf<Map<Uuid, Job?>>()
        backgroundScope.launch { f.runtime.getConversationJobs().collect { observed += it } }
        f.runtime.sendMessage(id, listOf(UIMessagePart.Text("question")))
        val job = assertNotNull(f.runtime.getGenerationJobStateFlow(id).first())
        select<Unit> {
            f.provider.entered.onAwait { }
            job.onJoin { error("Generation ended before calling provider: ${f.runtime.errors.value}") }
        }
        f.runtime.removeConversationReference(id)
        runCurrent()
        advanceTimeBy(6_000)
        runCurrent()
        assertSame(state, f.runtime.getConversationFlow(id))
        assertTrue(job.isActive)
        assertTrue(observed.any { it[id] === job })
        f.provider.response.complete(Unit)
        job.join()
        runCurrent()
        assertNull(f.runtime.getGenerationJobStateFlow(id).first())
        assertEquals(emptyMap(), observed.last())
        assertEquals("session completed", state.value.currentMessages.last().toText())
        assertEquals("session completed", f.repository.getConversationById(id)?.currentMessages?.last()?.toText())
        advanceTimeBy(5_000)
        runCurrent()
        assertNotSame(state, f.runtime.getConversationFlow(id))
        assertTrue(f.runtime.errors.value.isEmpty())
    }

    @Test
    fun `stopping generation cancels the provider and clears jobs without losing the held conversation`() = sessionTest { f ->
        val id = f.initialize()
        val state = f.runtime.getConversationFlow(id)
        f.runtime.sendMessage(id, listOf(UIMessagePart.Text("cancel me")))
        val job = assertNotNull(f.runtime.getGenerationJobStateFlow(id).first())
        select<Unit> {
            f.provider.entered.onAwait { }
            job.onJoin { error("Generation ended before calling provider: ${f.runtime.errors.value}") }
        }
        f.runtime.stopGeneration(id)
        job.join()
        runCurrent()
        assertTrue(job.isCancelled)
        assertNull(f.runtime.getGenerationJobStateFlow(id).first())
        assertEquals(emptyMap(), f.runtime.getConversationJobs().first())
        advanceTimeBy(6_000)
        runCurrent()
        assertSame(state, f.runtime.getConversationFlow(id))
        assertEquals("cancel me", f.repository.getConversationById(id)?.currentMessages?.last()?.toText())
        assertTrue(f.runtime.errors.value.isEmpty())
    }

    @Test
    fun `background title generation retains the session after the chat job ends`() = sessionTest { f ->
        val id = f.initialize(generateTitle = true)
        val state = f.runtime.getConversationFlow(id)
        f.runtime.sendMessage(id, listOf(UIMessagePart.Text("title question")))
        val job = assertNotNull(f.runtime.getGenerationJobStateFlow(id).first())
        select<Unit> {
            f.provider.entered.onAwait { }
            job.onJoin { error("Generation ended before provider: ${f.runtime.errors.value}") }
        }
        f.provider.response.complete(Unit)
        job.join()
        f.provider.backgroundEntered.await()
        f.runtime.removeConversationReference(id)
        runCurrent()
        advanceTimeBy(6_000)
        runCurrent()
        assertSame(state, f.runtime.getConversationFlow(id))
        assertNull(f.runtime.getGenerationJobStateFlow(id).first())
        f.provider.backgroundResponse.complete(Unit)
        state.first { it.title == "session completed" }
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()
        assertNotSame(state, f.runtime.getConversationFlow(id))
        assertEquals("session completed", f.repository.getConversationById(id)?.title)
        assertTrue(f.runtime.errors.value.isEmpty())
    }

    private fun sessionTest(block: suspend TestScope.(Fixture) -> Unit) = runTest(timeout = 10.seconds) {
        val fixture = Fixture(backgroundScope)
        try {
            runCurrent()
            block(fixture)
        } finally {
            backgroundScope.cancel()
            fixture.close()
        }
    }

    private class Fixture(scope: CoroutineScope) {
        init {
            startKoin { modules(module {
                single<SettingsStore> { settings }
                single<DocumentTextExtractor> { DocumentTextExtractor { _, _ -> error("No document expected") } }
                single<Base64ImageStore> { Base64ImageStore { error("No image expected") } }
            }) }
        }

        private val root = Files.createTempDirectory("cmp-session-").toFile()
        private val preferences = object : DataStore<Preferences> {
            override val data = MutableStateFlow(emptyPreferences())
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                transform(data.value).also { data.value = it }
        }
        val settings = SettingsStore(preferences, scope)
        private val database = buildAppDatabase(
            Room.inMemoryDatabaseBuilder<AppDatabase>(AppDatabaseConstructor::initialize),
            BundledSQLiteDriver(), MessageFtsDialect.UNICODE61,
        )
        private val attachments = SharedChatAttachmentStore(FileKitPlatformFileStore(PlatformFile(root)))
        val repository = ConversationRepository(
            database.conversationDao(), database.messageNodeDao(), database.favoriteDao(), database,
            FileKitFileCleaner(database, settings),
            MessageFtsManager(database, MessageFtsDialect.UNICODE61),
        )
        private val client = HttpClient(MockEngine { error("Session tests must not make network requests") })
        private val eventBus = AppEventBus()
        val provider = WaitingProvider()
        private val providers = ProviderManager(client).also { it.registerProvider("openai", provider) }
        val runtime = SharedChatRuntime(
            scope, settings, repository, FolderRepository(database.folderDao(), database.conversationDao()),
            providers, eventBus, DataStoreBooleanPreferenceStore(preferences),
            DataStoreStringPreferenceStore(preferences), attachments,
            McpManager(settings, scope, McpImageStore { _, _ -> error("No MCP image expected") },
                OAuthCallbackSessionFactory { error("No OAuth expected") }, client),
            TemplateTransformer(createMessageTemplateEngine().apply {
                val loader = AssistantTemplateLoader(settings)
                root = loader
                includes = loader
                layouts = loader
            }), LocalTools(eventBus, settings, null),
            MemoryRepository(database.memoryDao()), SkillManager(Path(root.path), settings),
        )

        suspend fun initialize(generateTitle: Boolean = false): Uuid {
            val model = Model(modelId = "session", displayName = "Session")
            val assistant = Assistant(name = "Session", chatModelId = model.id, streamOutput = false,
                localTools = emptyList(), enableMemory = false)
            settings.update(settings.settingsFlow.value.copy(
                init = false, providers = listOf(ProviderSetting.OpenAI(models = listOf(model))),
                assistants = listOf(assistant), assistantId = assistant.id, chatModelId = model.id,
                titleModelId = model.id.takeIf { generateTitle }, fastModelId = Uuid.random(), enableSuggestion = false,
            ))
            val id = Uuid.random()
            runtime.addConversationReference(id)
            runtime.initializeConversation(id)
            runtime.updateConversationState(id) { it.copy(title = if (generateTitle) "" else "Session") }
            return id
        }

        fun close() {
            stopKoin()
            client.close()
            database.close()
            root.deleteRecursively()
        }
    }

    private class WaitingProvider : Provider<ProviderSetting.OpenAI> {
        val entered = CompletableDeferred<Unit>()
        val response = CompletableDeferred<Unit>()
        val backgroundEntered = CompletableDeferred<Unit>()
        val backgroundResponse = CompletableDeferred<Unit>()
        private var calls = 0
        override suspend fun listModels(providerSetting: ProviderSetting.OpenAI): List<Model> = emptyList()
        override suspend fun generateText(
            providerSetting: ProviderSetting.OpenAI, messages: List<UIMessage>, params: TextGenerationParams,
        ): MessageChunk {
            if (calls++ == 0) {
                entered.complete(Unit)
                response.await()
            } else {
                backgroundEntered.complete(Unit)
                backgroundResponse.await()
            }
            return MessageChunk(id = "session", model = "session", choices = listOf(
                UIMessageChoice(index = 0, delta = null, message = UIMessage.assistant("session completed"),
                    finishReason = "stop"),
            ))
        }
        override suspend fun streamText(
            providerSetting: ProviderSetting.OpenAI, messages: List<UIMessage>, params: TextGenerationParams,
        ): Flow<MessageChunk> = emptyFlow()
        override suspend fun generateEmbedding(
            providerSetting: ProviderSetting.OpenAI, params: EmbeddingGenerationParams,
        ): EmbeddingGenerationResult = error("Not used")
    }
}
