package me.rerere.rikkahub.service

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import me.rerere.ai.core.TokenUsage
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageAnnotation
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
import me.rerere.rikkahub.utils.JsonInstant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class MessageTranslationPersistenceTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: ConversationRepository
    private lateinit var messageFtsManager: MessageFtsManager

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
        database.close()
    }

    @Test
    fun `translation updates an old alternative only and preserves all other persisted data`() = runTest {
        val otherMessage = UIMessage.user("unaffected message")
        val oldAlternative = UIMessage.assistant("translation persistence marker").copy(
            annotations = listOf(UIMessageAnnotation.UrlCitation("source", "https://example.com")),
            modelId = Uuid.random(),
            usage = TokenUsage(promptTokens = 3, completionTokens = 5, cachedTokens = 2, totalTokens = 8),
        )
        val selectedAlternative = UIMessage.assistant("newer alternative")
        val original = conversation(
            messageNodes = listOf(
                otherMessage.toMessageNode(),
                MessageNode(messages = listOf(oldAlternative, selectedAlternative), selectIndex = 1),
            ),
        )
        repository.insertConversation(original)
        val beforeConversationEntity = database.conversationDao().getConversationById(original.id.toString())!!
        val beforeFts = messageFtsManager.search("persistence")
        val translation = "译文：中文、emoji 🦜、引号\"、反斜杠\\、换行\n保留"

        assertTrue(
            repository.updateMessageTranslation(
                conversationId = original.id,
                expectedMessage = oldAlternative.copy(annotations = emptyList(), usage = null, translation = "stale"),
                translation = translation,
            )
        )

        val reloadedNode = database.messageNodeDao()
            .getNodesOfConversation(original.id.toString())
            .single { it.id == original.messageNodes[1].id.toString() }
            .let { JsonInstant.decodeFromString<List<UIMessage>>(it.messages) }
        assertEquals(otherMessage, repository.getConversationById(original.id)!!.messageNodes.first().messages.single())
        assertEquals(oldAlternative.copy(translation = translation), reloadedNode[0])
        assertEquals(selectedAlternative, reloadedNode[1])
        assertEquals(1, repository.getConversationById(original.id)!!.messageNodes[1].selectIndex)
        assertEquals(beforeConversationEntity, database.conversationDao().getConversationById(original.id.toString()))
        assertEquals(beforeFts, messageFtsManager.search("persistence"))
    }

    @Test
    fun `translation supports empty unicode values and clearing after database reload`() = runTest {
        val message = UIMessage.assistant("translation persistence marker")
        val original = conversation(messageNodes = listOf(message.toMessageNode()))
        repository.insertConversation(original)
        val unicodeTranslation = "翻译\n\"quoted\" \\ slash 🦜"

        assertTrue(repository.updateMessageTranslation(original.id, message, unicodeTranslation))
        assertEquals(unicodeTranslation, storedMessage(original).translation)

        assertTrue(repository.updateMessageTranslation(original.id, message, ""))
        assertEquals("", storedMessage(original).translation)

        assertTrue(repository.updateMessageTranslation(original.id, message, null))
        assertNull(storedMessage(original).translation)
    }

    @Test
    fun `deleted or content changed messages reject stale translation updates`() = runTest {
        val message = UIMessage.assistant("translation persistence marker")
        val original = conversation(messageNodes = listOf(message.toMessageNode()))
        repository.insertConversation(original)
        val edited = message.copy(parts = UIMessage.assistant("edited message").parts)
        repository.updateConversation(original.copy(messageNodes = listOf(edited.toMessageNode())))

        assertFalse(repository.updateMessageTranslation(original.id, message, "must not persist"))
        assertNull(storedMessage(original).translation)

        repository.deleteConversation(repository.getConversationById(original.id)!!)
        assertFalse(repository.updateMessageTranslation(original.id, edited, "orphaned"))
        assertNull(repository.getConversationById(original.id))
        assertTrue(messageFtsManager.search("persistence").isEmpty())
    }

    @Test
    fun `manager saves streamed translation then clear immediately updates memory and database`() = runTest {
        val translated = UIMessage.assistant("translation persistence marker")
        val otherAlternative = UIMessage.assistant("other alternative").copy(translation = "keep this")
        val original = conversation(
            messageNodes = listOf(
                MessageNode(messages = listOf(translated, otherAlternative), selectIndex = 1),
            ),
        )
        repository.insertConversation(original)
        val beforeConversationEntity = database.conversationDao().getConversationById(original.id.toString())!!
        val state = MutableStateFlow(original)
        val fixture = managerFixture(this, state) { _, _, _, _ -> flowOf("partial", "complete") }

        fixture.manager.translate(original.id, translated, "zh", "Chinese")!!.join()
        assertEquals("complete", stateTranslation(state, translated.id))
        assertEquals("complete", storedMessage(original, translated.id).translation)

        val clear = fixture.manager.clear(original.id, translated.id)!!
        assertNull(stateTranslation(state, translated.id))
        clear.join()

        assertNull(storedMessage(original, translated.id).translation)
        assertEquals("keep this", stateTranslation(state, otherAlternative.id))
        assertEquals("keep this", storedMessage(original, otherAlternative.id).translation)
        assertEquals(beforeConversationEntity, database.conversationDao().getConversationById(original.id.toString()))
        assertTrue(fixture.errors.isEmpty())
    }

    @Test
    fun `manager failure and cancellation clear partial and old database translations`() = runTest {
        listOf<Throwable>(IllegalStateException("translation failed"), CancellationException("translation cancelled"))
            .forEach { failure ->
                val message = UIMessage.assistant("translation persistence marker")
                    .copy(translation = "old translation")
                val original = conversation(messageNodes = listOf(message.toMessageNode()))
                repository.insertConversation(original)
                val state = MutableStateFlow(original)
                val fixture = managerFixture(this, state) { _, _, _, _ ->
                    flow {
                        emit("partial")
                        throw failure
                    }
                }

                val job = fixture.manager.translate(original.id, message, "zh", "Chinese")!!
                job.join()

                assertNull(stateTranslation(state, message.id))
                assertNull(storedMessage(original, message.id).translation)
                if (failure is CancellationException) {
                    assertTrue(job.isCancelled)
                    assertTrue(fixture.errors.isEmpty())
                } else {
                    assertFalse(job.isCancelled)
                    assertEquals(listOf(failure), fixture.errors)
                }
            }
    }

    private fun managerFixture(
        scope: CoroutineScope,
        state: MutableStateFlow<Conversation>,
        translator: (Settings, String, String, String) -> Flow<String>,
    ): ManagerFixture {
        val errors = mutableListOf<Throwable>()
        return ManagerFixture(
            manager = MessageTranslationManager(
                scope = scope,
                getSettings = { Settings() },
                translateText = translator,
                getConversation = { conversationId -> state.value.takeIf { it.id == conversationId } },
                updateTranslation = { _, expectedMessage, translation ->
                    state.value = state.value.withMessageTranslation(expectedMessage, translation)
                },
                saveTranslation = repository::updateMessageTranslation,
                getLoadingText = { "Translating" },
                onError = { _, error -> errors += error },
            ),
            errors = errors,
        )
    }

    private fun stateTranslation(state: MutableStateFlow<Conversation>, messageId: Uuid): String? = state.value
        .getMessageNodeByMessageId(messageId)
        ?.messages
        ?.firstOrNull { it.id == messageId }
        ?.translation

    private suspend fun storedMessage(conversation: Conversation): UIMessage = database.messageNodeDao()
        .getNodesOfConversation(conversation.id.toString())
        .single()
        .let { JsonInstant.decodeFromString<List<UIMessage>>(it.messages).single() }

    private suspend fun storedMessage(conversation: Conversation, messageId: Uuid): UIMessage =
        database.messageNodeDao()
            .getNodesOfConversation(conversation.id.toString())
            .flatMap { JsonInstant.decodeFromString<List<UIMessage>>(it.messages) }
            .single { it.id == messageId }

    private fun conversation(messageNodes: List<MessageNode>): Conversation = Conversation(
        id = Uuid.random(),
        assistantId = Uuid.random(),
        title = "Original title",
        messageNodes = messageNodes,
        chatSuggestions = listOf("original suggestion"),
        isPinned = true,
        createAt = Instant.fromEpochMilliseconds(1_000),
        updateAt = Instant.fromEpochMilliseconds(2_000),
        customSystemPrompt = "system prompt",
        modeInjectionIds = setOf(Uuid.random()),
        lorebookIds = setOf(Uuid.random()),
        workspaceCwd = "/workspace/path",
        folderId = Uuid.random(),
    )

    private class ManagerFixture(
        val manager: MessageTranslationManager,
        val errors: List<Throwable>,
    )
}
