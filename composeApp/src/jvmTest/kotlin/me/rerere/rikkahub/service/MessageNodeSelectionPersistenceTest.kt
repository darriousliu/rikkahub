package me.rerere.rikkahub.service

import me.rerere.rikkahub.data.files.testFilesManager
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.test.runTest
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.AppDatabaseConstructor
import me.rerere.rikkahub.data.db.buildAppDatabase
import me.rerere.rikkahub.data.db.fts.MessageFtsDialect
import me.rerere.rikkahub.data.db.fts.MessageFtsManager
import me.rerere.rikkahub.data.repository.ConversationRepository
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MessageNodeSelectionPersistenceTest {
    @Test
    fun `selection survives reopening SQLite and keeps both alternatives and metadata`() = runTest {
        val directory = Files.createTempDirectory("rikkahub-node-selection").toFile()
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

            val original = selectionConversation()
            val target = original.messageNodes[1]
            val firstDatabase = openDatabase().also { database = it }
            val repository = repository(firstDatabase)
            repository.insertConversation(original)
            val selected = ChatServiceTestFixture(existingDatabase = firstDatabase, existingRepository = repository).use {
                it.service.updateConversationState(original.id) { original }
                it.service.selectMessageNode(original.id, target.id, 1)
                it.service.getConversationFlow(original.id).value
            }
            firstDatabase.close()
            database = null

            val reopenedDatabase = openDatabase().also { database = it }
            val restored = repository(reopenedDatabase).getConversationById(original.id)!!
            assertEquals(selected, restored)
            assertEquals(target.messages[1], restored.currentMessages[1])
            assertEquals(target.messages, restored.messageNodes[1].messages)
            assertEquals(original.copy(messageNodes = restored.messageNodes), restored)
            val search = MessageFtsManager(reopenedDatabase, MessageFtsDialect.UNICODE61)
            assertEquals(target.messages[0].id.toString(), search.search("originalmarker").single().messageId)
            assertEquals(target.messages[1].id.toString(), search.search("alternativemarker").single().messageId)
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
        filesManager = testFilesManager(),
        messageFtsManager = MessageFtsManager(database, MessageFtsDialect.UNICODE61),
    )
}
