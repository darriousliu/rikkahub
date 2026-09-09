package me.rerere.rikkahub.service

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.AppDatabaseConstructor
import me.rerere.rikkahub.data.db.buildAppDatabase
import me.rerere.rikkahub.data.db.fts.MessageFtsDialect
import me.rerere.rikkahub.data.db.fts.MessageFtsManager
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.repository.ConversationFileStore
import me.rerere.rikkahub.data.repository.ConversationRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

class MessageTranslationPersistenceTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: ConversationRepository

    @BeforeTest
    fun setUp() {
        database = buildAppDatabase(
            Room.inMemoryDatabaseBuilder<AppDatabase>(AppDatabaseConstructor::initialize),
            BundledSQLiteDriver(),
            MessageFtsDialect.UNICODE61,
        )
        repository = ConversationRepository(
            database.conversationDao(),
            database.messageNodeDao(),
            database.favoriteDao(),
            database,
            ConversationFileStore {},
            MessageFtsManager(database, MessageFtsDialect.UNICODE61),
        )
    }

    @AfterTest
    fun tearDown() = database.close()

    @Test
    fun `completed translation saves its complete conversation`() = runTest {
        val target = UIMessage.assistant("Hello")
        val other = UIMessage.assistant("Other").copy(translation = "keep")
        val original = Conversation.ofId(
            Uuid.random(),
            messages = listOf(MessageNode(messages = listOf(target, other), selectIndex = 1)),
        ).copy(title = "Existing title", chatSuggestions = listOf("Existing suggestion"))
        repository.insertConversation(original)
        val state = MutableStateFlow(original)
        manager(this, state) { flowOf("你", "你好") }.manager.translate(original.id, target, "zh", "Chinese").join()
        val saved = repository.getConversationById(original.id)!!
        assertEquals("你好", translation(saved, target.id))
        assertEquals("keep", translation(saved, other.id))
        assertEquals(1, saved.messageNodes.single().selectIndex)
        assertEquals(original.title, saved.title)
        assertEquals(original.chatSuggestions, saved.chatSuggestions)
        val persistedTarget = saved.messageNodes.single().messages.first()
        assertEquals(target, persistedTarget.copy(translation = null))
    }

    @Test
    fun `clear and errors stay in memory until an ordinary conversation save`() = runTest {
        val target = UIMessage.assistant("Hello").copy(translation = "old")
        val original = Conversation.ofId(
            Uuid.random(),
            messages = listOf(MessageNode(messages = listOf(target))),
        )
        repository.insertConversation(original)
        val state = MutableStateFlow(original)
        val fixture = manager(this, state) { throw IllegalStateException("failed") }
        fixture.manager.clear(original.id, target.id)
        assertNull(translation(state.value, target.id))
        assertEquals("old", translation(repository.getConversationById(original.id)!!, target.id))
        repository.updateConversation(state.value)
        assertNull(translation(repository.getConversationById(original.id)!!, target.id))
        state.value = original
        repository.updateConversation(original)
        fixture.manager.translate(original.id, target, "zh", "Chinese").join()
        assertNull(translation(state.value, target.id))
        assertEquals("old", translation(repository.getConversationById(original.id)!!, target.id))
        assertEquals(listOf("failed"), fixture.errors.map { it.message })
        repository.updateConversation(state.value)
        assertNull(translation(repository.getConversationById(original.id)!!, target.id))
    }

    private fun manager(
        scope: CoroutineScope,
        state: MutableStateFlow<Conversation>,
        source: () -> Flow<String>,
    ): Fixture {
        val errors = mutableListOf<Throwable>()
        val manager = MessageTranslationManager(
            scope = scope,
            getSettings = { Settings() },
            translateText = { _, _, _, _, callback ->
                flow {
                    source().collect { text ->
                        callback?.invoke(text)
                        emit(text)
                    }
                }
            },
            getConversation = { state.value },
            updateConversation = { _, conversation -> state.value = conversation },
            saveConversation = { _, conversation -> repository.updateConversation(conversation) },
            getLoadingText = { "Translating" },
            onError = { _, error -> errors += error },
        )
        return Fixture(manager, errors)
    }

    private fun translation(conversation: Conversation, id: Uuid): String? =
        conversation.getMessageNodeByMessageId(id)?.messages?.first { it.id == id }?.translation

    private class Fixture(val manager: MessageTranslationManager, val errors: List<Throwable>)
}
