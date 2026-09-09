package me.rerere.rikkahub.service

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.AppDatabaseConstructor
import me.rerere.rikkahub.data.db.buildAppDatabase
import me.rerere.rikkahub.data.db.fts.MessageFtsDialect
import me.rerere.rikkahub.data.db.fts.MessageFtsManager
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.toMessageNode
import me.rerere.rikkahub.data.repository.ConversationFileStore
import me.rerere.rikkahub.data.repository.ConversationRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class ConversationCompressionPersistenceTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: ConversationRepository
    private lateinit var fts: MessageFtsManager
    private val clients = mutableListOf<HttpClient>()

    @BeforeTest
    fun setUp() {
        database = buildAppDatabase(
            builder = Room.inMemoryDatabaseBuilder<AppDatabase>(AppDatabaseConstructor::initialize),
            driver = BundledSQLiteDriver(),
            ftsDialect = MessageFtsDialect.UNICODE61,
        )
        fts = MessageFtsManager(database, MessageFtsDialect.UNICODE61)
        repository = ConversationRepository(
            conversationDAO = database.conversationDao(),
            messageNodeDAO = database.messageNodeDao(),
            favoriteDAO = database.favoriteDao(),
            database = database,
            conversationFileStore = ConversationFileStore {},
            messageFtsManager = fts,
        )
    }

    @AfterTest
    fun tearDown() {
        clients.forEach(HttpClient::close)
        database.close()
    }

    @Test
    fun `shared compression saves summary and recent message using the existing repository path`() = runTest {
        val original = conversation()
        repository.insertConversation(original)
        val state = MutableStateFlow(original)

        assertTrue(compressor(state).compress(original.id, original, "", 500, 1).isSuccess)

        val reloaded = repository.getConversationById(original.id)!!
        assertEquals(listOf("compressedmarker", "recentmarker"), reloaded.currentMessages.map(UIMessage::toText))
        assertEquals(original.currentMessages.last(), reloaded.currentMessages.last())
        assertEquals(original.copy(messageNodes = reloaded.messageNodes, chatSuggestions = emptyList()), reloaded)
        assertEquals(state.value, reloaded)
        assertTrue(fts.search("oldmarker").isEmpty())
        assertEquals(1, fts.search("compressedmarker").size)
        assertEquals(original.currentMessages.last().id.toString(), fts.search("recentmarker").single().messageId)
    }

    @Test
    fun `provider failure does not reach the existing save path`() = runTest {
        val original = conversation()
        repository.insertConversation(original)
        val state = MutableStateFlow(original)

        assertTrue(compressor(state, failRequest = true).compress(original.id, original, "", 500, 1).isFailure)

        assertEquals(original, state.value)
        assertEquals(original, repository.getConversationById(original.id))
        assertEquals(1, fts.search("oldmarker").size)
    }

    private fun compressor(
        state: MutableStateFlow<Conversation>,
        failRequest: Boolean = false,
    ): ConversationCompressor {
        val model = Model(modelId = "fake-compression", displayName = "Fake")
        val client = HttpClient(MockEngine {
            if (failRequest) error("Test provider failure")
            respond(
                content = """
                    {"id":"fake","model":"fake-compression","choices":[
                      {"index":0,"message":{"role":"assistant","content":"compressedmarker"},"finish_reason":"stop"}
                    ]}
                """.trimIndent(),
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }).also(clients::add)
        val settings = Settings(
            providers = listOf(ProviderSetting.OpenAI(models = listOf(model), baseUrl = "https://fake.invalid/v1")),
            compressModelId = model.id,
        )
        return ConversationCompressor(
            providerManager = ProviderManager(client),
            getSettings = { settings },
            saveConversation = { id, compressed ->
                if (repository.existsConversationById(id)) {
                    repository.updateConversation(compressed)
                } else {
                    repository.insertConversation(compressed)
                }
                state.value = compressed
            },
            getNotEnoughMessagesText = { "Not enough messages" },
            getLocaleName = { "Test Locale" },
        )
    }

    private fun conversation() = Conversation.ofId(
        id = Uuid.random(),
        messages = listOf(UIMessage.user("oldmarker").toMessageNode(), UIMessage.assistant("recentmarker").toMessageNode()),
    ).copy(
        title = "Original title",
        chatSuggestions = listOf("Old suggestion"),
        createAt = Instant.parse("2025-01-02T03:04:05Z"),
        updateAt = Instant.parse("2025-02-03T04:05:06Z"),
        customSystemPrompt = "System prompt",
        modeInjectionIds = setOf(Uuid.random()),
        lorebookIds = setOf(Uuid.random()),
        workspaceCwd = "/workspace/unchanged",
    )
}
