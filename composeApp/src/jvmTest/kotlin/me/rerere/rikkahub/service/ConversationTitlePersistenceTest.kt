package me.rerere.rikkahub.service

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.test.runTest
import me.rerere.ai.ui.UIMessage
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class ConversationTitlePersistenceTest {
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
    fun `title CAS changes only title and updates search metadata`() = runTest {
        val original = conversation(title = "Old title")
        repository.insertConversation(original)

        val updated = repository.updateConversationTitle(
            conversationId = original.id,
            expectedTitle = "Old title",
            title = "Generated title",
        )

        assertTrue(updated)
        assertEquals(original.copy(title = "Generated title"), repository.getConversationById(original.id))
        val searchResult = messageFtsManager.search("persistence").single()
        assertEquals("Generated title", searchResult.title)
        assertTrue("persistence" in searchResult.snippet)
    }

    @Test
    fun `stale expected title changes neither conversation nor search metadata`() = runTest {
        val original = conversation(title = "User title")
        repository.insertConversation(original)

        val updated = repository.updateConversationTitle(
            conversationId = original.id,
            expectedTitle = "Old title",
            title = "Generated title",
        )

        assertFalse(updated)
        assertEquals(original, repository.getConversationById(original.id))
        assertEquals("User title", messageFtsManager.search("persistence").single().title)
    }

    @Test
    fun `deleted conversation is not recreated`() = runTest {
        val original = conversation(title = "Old title")
        repository.insertConversation(original)
        repository.deleteConversation(original)

        val updated = repository.updateConversationTitle(
            conversationId = original.id,
            expectedTitle = "Old title",
            title = "Generated title",
        )

        assertFalse(updated)
        assertNull(repository.getConversationById(original.id))
        assertTrue(messageFtsManager.search("persistence").isEmpty())
    }

    private fun conversation(title: String): Conversation = Conversation(
        id = Uuid.random(),
        assistantId = Uuid.random(),
        title = title,
        messageNodes = listOf(
            UIMessage.user("unique persistence marker").toMessageNode(),
            UIMessage.assistant("response body").toMessageNode(),
        ),
        chatSuggestions = listOf("suggestion"),
        isPinned = true,
        createAt = Instant.fromEpochMilliseconds(1_000),
        updateAt = Instant.fromEpochMilliseconds(2_000),
        customSystemPrompt = "system prompt",
        modeInjectionIds = setOf(Uuid.random()),
        lorebookIds = setOf(Uuid.random()),
        workspaceCwd = "/workspace/path",
        folderId = Uuid.random(),
    )
}
