package me.rerere.rikkahub.service

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.test.runTest
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

class InvalidMessageCleanupPersistenceTest {
    @Test
    fun `saving cleaned nodes survives SQLite reopen and removes deleted branches from search`() = runTest {
        val directory = Files.createTempDirectory("rikkahub-invalid-message-cleanup").toFile()
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

            val original = invalidMessageCleanupConversation()
            val firstDatabase = openDatabase().also { database = it }
            val repository = repository(firstDatabase)
            repository.insertConversation(original)
            val cleaned = buildConversationAfterInvalidMessageCleanup(original)
            repository.updateConversation(cleaned)
            firstDatabase.close()
            database = null

            val reopenedDatabase = openDatabase().also { database = it }
            val restored = assertNotNull(repository(reopenedDatabase).getConversationById(original.id))
            assertEquals(cleaned, restored)
            assertEquals(original.copy(messageNodes = restored.messageNodes), restored)
            assertEquals(3, restored.messageNodes.size)
            val target = original.messageNodes[1]
            assertEquals(listOf(target.messages[0], target.messages[2]), restored.messageNodes[1].messages)
            assertEquals(0, restored.messageNodes[1].selectIndex)
            assertEquals(original.messageNodes[3], restored.messageNodes[2])
            assertEquals(listOf("file:///shared-cleanup.png", "file:///shared-cleanup.png"), restored.files)
            val search = MessageFtsManager(reopenedDatabase, MessageFtsDialect.UNICODE61)
            assertTrue(search.search("invalidbranchmarker").isEmpty())
            assertTrue(search.search("invalidnodemarker").isEmpty())
            assertEquals(target.messages[0].id.toString(), search.search("keepbeforemarker").single().messageId)
            assertEquals(target.messages[2].id.toString(), search.search("keepaftermarker").single().messageId)
            assertEquals(
                original.messageNodes[3].currentMessage.id.toString(),
                search.search("resumablemarker").single().messageId,
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
        conversationFileStore = ConversationFileStore { error("Calculating and saving nodes must not delete files") },
        messageFtsManager = MessageFtsManager(database, MessageFtsDialect.UNICODE61),
    )
}
