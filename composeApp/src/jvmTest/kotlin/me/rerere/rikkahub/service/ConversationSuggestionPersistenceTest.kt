package me.rerere.rikkahub.service

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
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
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.AppDatabaseConstructor
import me.rerere.rikkahub.data.db.buildAppDatabase
import me.rerere.rikkahub.data.db.fts.MessageFtsDialect
import me.rerere.rikkahub.data.db.fts.MessageFtsManager
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.model.toMessageNode
import me.rerere.rikkahub.data.repository.ConversationFileStore
import me.rerere.rikkahub.data.repository.ConversationRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class ConversationSuggestionPersistenceTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: ConversationRepository
    private lateinit var messageFtsManager: MessageFtsManager
    private val clients = mutableListOf<HttpClient>()

    @BeforeTest
    fun setUp() {
        database = buildAppDatabase(
            builder = Room.inMemoryDatabaseBuilder<AppDatabase>(AppDatabaseConstructor::initialize),
            driver = BundledSQLiteDriver(),
            ftsDialect = MessageFtsDialect.UNICODE61,
        )
        messageFtsManager = MessageFtsManager(database, MessageFtsDialect.UNICODE61)
        repository = ConversationRepository(
            conversationDAO = database.conversationDao(),
            messageNodeDAO = database.messageNodeDao(),
            favoriteDAO = database.favoriteDao(),
            database = database,
            conversationFileStore = ConversationFileStore {},
            messageFtsManager = messageFtsManager,
        )
    }

    @AfterTest
    fun tearDown() {
        clients.forEach { it.close() }
        clients.clear()
        database.close()
    }

    @Test
    fun `suggestion update changes only suggestions and preserves metadata messages and FTS`() = runTest {
        val original = conversation()
        repository.insertConversation(original)
        assertTrue(repository.updateConversationTitle(original.id, original.title, "User-renamed title"))
        database.conversationDao().updatePinStatus(original.id.toString(), false)
        val replacementFolderId = Uuid.random()
        database.conversationDao().updateFolderId(original.id.toString(), replacementFolderId.toString())
        val before = repository.getConversationById(original.id)!!
        val beforeEntity = database.conversationDao().getConversationById(original.id.toString())!!
        val beforeFts = messageFtsManager.search("persistence")
        assertEquals(original.currentMessages, before.currentMessages)

        val updated = repository.updateConversationSuggestions(
            conversationId = original.id,
            expectedMessages = original.currentMessages,
            suggestions = listOf("first", "second"),
        )

        assertTrue(updated)
        assertEquals(
            before.copy(chatSuggestions = listOf("first", "second")),
            repository.getConversationById(original.id),
        )
        val afterEntity = database.conversationDao().getConversationById(original.id.toString())!!
        assertEquals(beforeEntity, afterEntity.copy(chatSuggestions = beforeEntity.chatSuggestions))
        assertEquals(beforeFts, messageFtsManager.search("persistence"))
    }

    @Test
    fun `stale snapshot is rejected after appending a message or editing content with the same id`() = runTest {
        val appendedConversation = conversation()
        repository.insertConversation(appendedConversation)
        repository.updateConversation(
            appendedConversation.copy(
                messageNodes = appendedConversation.messageNodes +
                    UIMessage.user("newer question").toMessageNode(),
            )
        )

        assertFalse(
            repository.updateConversationSuggestions(
                appendedConversation.id,
                appendedConversation.currentMessages,
                listOf("stale after append"),
            )
        )
        assertEquals(
            listOf("initial suggestion"),
            repository.getConversationById(appendedConversation.id)!!.chatSuggestions,
        )

        val editedConversation = conversation()
        repository.insertConversation(editedConversation)
        val editedMessage = editedConversation.currentMessages.last().copy(
            parts = UIMessage.assistant("edited response body").parts,
        )
        repository.updateConversation(
            editedConversation.copy(
                messageNodes = editedConversation.messageNodes.dropLast(1) + editedMessage.toMessageNode(),
            )
        )

        assertFalse(
            repository.updateConversationSuggestions(
                editedConversation.id,
                editedConversation.currentMessages,
                listOf("stale after edit"),
            )
        )
        assertEquals(
            listOf("initial suggestion"),
            repository.getConversationById(editedConversation.id)!!.chatSuggestions,
        )
    }

    @Test
    fun `stale snapshot is rejected after switching message branch`() = runTest {
        val firstBranch = UIMessage.assistant("first branch")
        val secondBranch = UIMessage.assistant("second branch")
        val original = conversation().copy(
            messageNodes = listOf(
                UIMessage.user("unique persistence marker").toMessageNode(),
                MessageNode(messages = listOf(firstBranch, secondBranch), selectIndex = 0),
            )
        )
        repository.insertConversation(original)
        repository.updateConversation(
            original.copy(
                messageNodes = original.messageNodes.dropLast(1) + original.messageNodes.last().copy(selectIndex = 1),
            )
        )

        val updated = repository.updateConversationSuggestions(
            original.id,
            original.currentMessages,
            listOf("stale branch suggestion"),
        )

        assertFalse(updated)
        assertEquals(listOf("initial suggestion"), repository.getConversationById(original.id)!!.chatSuggestions)
    }

    @Test
    fun `deleted conversation is not recreated`() = runTest {
        val original = conversation()
        repository.insertConversation(original)
        repository.deleteConversation(original)

        val updated = repository.updateConversationSuggestions(
            original.id,
            original.currentMessages,
            listOf("orphaned suggestion"),
        )

        assertFalse(updated)
        assertNull(repository.getConversationById(original.id))
        assertTrue(messageFtsManager.search("persistence").isEmpty())
    }

    @Test
    fun `empty and JSON-sensitive suggestions round trip`() = runTest {
        val original = conversation()
        repository.insertConversation(original)
        val specialSuggestions = listOf(
            "quoted \"text\" and a backslash \\",
            "line one\nline two",
            "emoji 🦜 and 中文",
        )

        assertTrue(repository.updateConversationSuggestions(original.id, original.currentMessages, specialSuggestions))
        assertEquals(specialSuggestions, repository.getConversationById(original.id)!!.chatSuggestions)

        assertTrue(repository.updateConversationSuggestions(original.id, original.currentMessages, emptyList()))
        assertEquals(emptyList(), repository.getConversationById(original.id)!!.chatSuggestions)
    }

    @Test
    fun `generator persists clearing before provider failure or cancellation`() = runTest {
        val failures = listOf(
            IllegalStateException("provider failed"),
            CancellationException("provider cancelled"),
        )
        failures.forEach { failure ->
            val original = conversation()
            repository.insertConversation(original)
            val fixture = integrationFixture { _, _, _ ->
                assertEquals(emptyList(), repository.getConversationById(original.id)!!.chatSuggestions)
                throw failure
            }

            val actual = runCatching {
                fixture.generator.generate(original.id, original)
            }.exceptionOrNull()

            if (failure is CancellationException) {
                assertIs<CancellationException>(actual)
            } else {
                assertSame(failure, actual)
            }
            assertEquals(emptyList(), repository.getConversationById(original.id)!!.chatSuggestions)
        }
    }

    @Test
    fun `generator leaves persisted suggestions empty when disabled during generation`() = runTest {
        val original = conversation()
        repository.insertConversation(original)
        lateinit var fixture: GeneratorFixture
        fixture = integrationFixture { _, _, _ ->
            assertEquals(emptyList(), repository.getConversationById(original.id)!!.chatSuggestions)
            fixture.settings = fixture.settings.copy(enableSuggestion = false)
            suggestionResponse("stale suggestion")
        }

        fixture.generator.generate(original.id, original)

        assertEquals(emptyList(), repository.getConversationById(original.id)!!.chatSuggestions)
    }

    private fun integrationFixture(
        handler: suspend (ProviderSetting.OpenAI, List<UIMessage>, TextGenerationParams) -> MessageChunk,
    ): GeneratorFixture {
        val suggestionModel = Model(modelId = "suggestion", displayName = "Suggestion")
        val providerSetting = ProviderSetting.OpenAI(models = listOf(suggestionModel))
        val provider = FakeProvider(handler)
        val client = HttpClient(MockEngine { error("Unexpected HTTP request") }).also { clients += it }
        val providerManager = ProviderManager(client).apply { registerProvider("openai", provider) }
        val fixture = GeneratorFixture(
            settings = Settings(
                enableSuggestion = true,
                suggestionModelId = suggestionModel.id,
                fastModelId = suggestionModel.id,
                suggestionPrompt = "{content}",
                providers = listOf(providerSetting),
            )
        )
        fixture.generator = ConversationSuggestionGenerator(
            providerManager = providerManager,
            getSettings = { fixture.settings },
            getConversation = repository::getConversationById,
            clearSuggestions = { id, expectedMessages ->
                repository.updateConversationSuggestions(id, expectedMessages, emptyList())
            },
            saveSuggestions = { id, expectedMessages, suggestions ->
                repository.updateConversationSuggestions(id, expectedMessages, suggestions)
            },
            getLocaleName = { "English (Test)" },
        )
        return fixture
    }

    private class GeneratorFixture(var settings: Settings) {
        lateinit var generator: ConversationSuggestionGenerator
    }

    private class FakeProvider(
        private val handler: suspend (ProviderSetting.OpenAI, List<UIMessage>, TextGenerationParams) -> MessageChunk,
    ) : Provider<ProviderSetting.OpenAI> {
        override suspend fun listModels(providerSetting: ProviderSetting.OpenAI): List<Model> = emptyList()

        override suspend fun generateText(
            providerSetting: ProviderSetting.OpenAI,
            messages: List<UIMessage>,
            params: TextGenerationParams,
        ): MessageChunk = handler(providerSetting, messages, params)

        override suspend fun streamText(
            providerSetting: ProviderSetting.OpenAI,
            messages: List<UIMessage>,
            params: TextGenerationParams,
        ): Flow<MessageChunk> = emptyFlow()

        override suspend fun generateEmbedding(
            providerSetting: ProviderSetting.OpenAI,
            params: EmbeddingGenerationParams,
        ): EmbeddingGenerationResult = error("Unused")
    }

    private fun conversation(): Conversation = Conversation(
        id = Uuid.random(),
        assistantId = Uuid.random(),
        title = "Original title",
        messageNodes = listOf(
            UIMessage.user("unique persistence marker").toMessageNode(),
            UIMessage.assistant("response body").toMessageNode(),
        ),
        chatSuggestions = listOf("initial suggestion"),
        isPinned = true,
        createAt = Instant.fromEpochMilliseconds(1_000),
        updateAt = Instant.fromEpochMilliseconds(2_000),
        customSystemPrompt = "system prompt",
        modeInjectionIds = setOf(Uuid.random()),
        lorebookIds = setOf(Uuid.random()),
        workspaceCwd = "/workspace/path",
        folderId = Uuid.random(),
    )

    private fun suggestionResponse(message: String): MessageChunk = MessageChunk(
        id = "response",
        model = "fake",
        choices = listOf(
            UIMessageChoice(
                index = 0,
                delta = null,
                message = UIMessage.assistant(message),
                finishReason = "stop",
            )
        ),
    )
}
