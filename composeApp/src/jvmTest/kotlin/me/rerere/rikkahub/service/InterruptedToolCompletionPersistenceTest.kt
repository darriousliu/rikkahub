package me.rerere.rikkahub.service

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.test.runTest
import me.rerere.ai.ui.ToolApprovalState
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.AppDatabaseConstructor
import me.rerere.rikkahub.data.db.buildAppDatabase
import me.rerere.rikkahub.data.db.fts.MessageFtsDialect
import me.rerere.rikkahub.data.db.fts.MessageFtsManager
import me.rerere.rikkahub.data.repository.ConversationFileStore
import me.rerere.rikkahub.data.repository.ConversationRepository
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class InterruptedToolCompletionPersistenceTest {
    @Test
    fun `cancelled tool output and completion times survive SQLite reopen`() = runTest {
        val directory = Files.createTempDirectory("rikkahub-interrupted-tools").toFile()
        var database: AppDatabase? = null
        try {
            fun openDatabase(): AppDatabase = buildAppDatabase(
                builder = Room.databaseBuilder<AppDatabase>(
                    name = directory.resolve("conversation.db").absolutePath,
                    factory = AppDatabaseConstructor::initialize,
                ),
                driver = BundledSQLiteDriver(),
                ftsDialect = MessageFtsDialect.UNICODE61,
            )

            val original = interruptedToolConversation()
            val firstDatabase = openDatabase().also { database = it }
            val repository = repository(firstDatabase)
            repository.insertConversation(original)
            val completed = ChatServiceTestFixture(existingDatabase = firstDatabase, existingRepository = repository).use {
                it.service.updateConversationState(original.id) { original }
                it.service.finishInterruptedPendingTools(original.id)
                it.service.getConversationFlow(original.id).value
            }
            firstDatabase.close()
            database = null

            val reopenedDatabase = openDatabase().also { database = it }
            val restored = assertNotNull(repository(reopenedDatabase).getConversationById(original.id))
            assertEquals(completed, restored)
            assertEquals(original.copy(messageNodes = restored.messageNodes), restored)
            assertEquals(original.messageNodes.first(), restored.messageNodes.first())
            assertEquals(original.messageNodes.last().messages[0], restored.messageNodes.last().messages[0])
            assertEquals(1, restored.messageNodes.last().selectIndex)
            val message = restored.messageNodes.last().currentMessage
            assertNotNull(message.finishedAt)
            assertTrue(message.parts.filterIsInstance<UIMessagePart.Reasoning>().all { it.finishedAt != null })
            val tools = message.parts.filterIsInstance<UIMessagePart.Tool>()
            assertEquals(6, tools.size)
            assertTrue(tools.all { it.isExecuted })
            assertTrue(tools.take(5).all {
                it.approvalState == ToolApprovalState.Denied("Generation cancelled by user")
            })
            assertEquals(original.files, restored.files)
            val search = MessageFtsManager(reopenedDatabase, MessageFtsDialect.UNICODE61)
            assertEquals(message.id.toString(), search.search("currentmarker").single().messageId)
            assertEquals(
                original.messageNodes.last().messages[0].id.toString(),
                search.search("alternativemarker").single().messageId,
            )
        } finally {
            database?.close()
            assertTrue(directory.deleteRecursively(), "Temporary SQLite files should be removed")
        }
    }

    private fun repository(database: AppDatabase) = ConversationRepository(
        conversationDAO = database.conversationDao(),
        messageNodeDAO = database.messageNodeDao(),
        favoriteDAO = database.favoriteDao(),
        database = database,
        conversationFileStore = ConversationFileStore { error("Tool completion must not delete files") },
        messageFtsManager = MessageFtsManager(database, MessageFtsDialect.UNICODE61),
    )
}
