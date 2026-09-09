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

class MessageDeletionPersistenceTest {
    @Test
    fun `branch and node deletion survive SQLite reopen with retained metadata and updated search`() = runTest {
        val directory = Files.createTempDirectory("rikkahub-message-deletion").toFile()
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

            val original = deletionConversation()
            val target = original.messageNodes[1]
            val firstDatabase = openDatabase().also { database = it }
            val repository = repository(firstDatabase)
            repository.insertConversation(original)
            val afterBranch = assertNotNull(buildConversationAfterMessageDelete(original, target.messages[0].id))
            repository.updateConversation(afterBranch)
            val persistedBranch = assertNotNull(repository.getConversationById(original.id))
            assertEquals(afterBranch, persistedBranch)

            val afterNode = assertNotNull(
                buildConversationAfterMessageDelete(persistedBranch, original.messageNodes.last().currentMessage.id),
            )
            repository.updateConversation(afterNode)
            firstDatabase.close()
            database = null

            val reopenedDatabase = openDatabase().also { database = it }
            val restored = assertNotNull(repository(reopenedDatabase).getConversationById(original.id))
            assertEquals(afterNode, restored)
            assertEquals(original.copy(messageNodes = restored.messageNodes), restored)
            assertEquals(2, restored.messageNodes.size)
            assertEquals(target.messages.drop(1), restored.messageNodes[1].messages)
            assertEquals(1, restored.messageNodes[1].selectIndex)
            assertEquals(target.messages[2], restored.currentMessages[1])
            assertEquals(listOf("file:///shared-image.png", "file:///shared-image.png"), restored.files)
            val search = MessageFtsManager(reopenedDatabase, MessageFtsDialect.UNICODE61)
            assertTrue(search.search("firstdeletemarker").isEmpty())
            assertTrue(search.search("lastnodemarker").isEmpty())
            assertEquals(target.messages[1].id.toString(), search.search("keepsecondmarker").single().messageId)
            assertEquals(target.messages[2].id.toString(), search.search("keepthirdmarker").single().messageId)
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
