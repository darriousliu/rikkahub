package me.rerere.rikkahub.data.db.migrations

import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.async.prepare
import androidx.sqlite.async.step
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.TokenUsage
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.common.logging.RikkaLog as Log
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.AppDatabaseConstructor
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.uuid.Uuid

@RunWith(AndroidJUnit4::class)
class Migration_11_12_Test {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val testDatabaseName = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = instrumentation,
        file = instrumentation.targetContext.getDatabasePath(testDatabaseName),
        driver = BundledSQLiteDriver(),
        databaseClass = AppDatabase::class,
        databaseFactory = AppDatabaseConstructor::initialize,
    )

    @Before
    fun deleteDatabase() {
        instrumentation.targetContext.deleteDatabase(testDatabaseName)
    }

    @Test
    fun migrate11To12_createsMessageNodeTableWithCorrectSchema() = runBlocking {
        helper.createDatabase(11).close()

        val db = helper.runMigrationsAndValidate(12, listOf(Migration_11_12))
        val columnNames = db.prepare("SELECT * FROM message_node LIMIT 0").use { it.getColumnNames() }

        assertTrue("message_node table should exist", columnNames.isNotEmpty())
        assertTrue("Should have 'id' column", columnNames.contains("id"))
        assertTrue("Should have 'conversation_id' column", columnNames.contains("conversation_id"))
        assertTrue("Should have 'node_index' column", columnNames.contains("node_index"))
        assertTrue("Should have 'messages' column", columnNames.contains("messages"))
        assertTrue("Should have 'select_index' column", columnNames.contains("select_index"))

        db.close()
    }

    @Test
    fun migrate11To12_migratesSimpleConversationCorrectly() = runBlocking {
        val conversationId = Uuid.random().toString()
        val messageNodes = listOf(
            MessageNode(
                id = Uuid.random(),
                messages = listOf(
                    UIMessage(
                        role = MessageRole.USER,
                        parts = listOf(UIMessagePart.Text("Hello")),
                    )
                ),
                selectIndex = 0,
            ),
            MessageNode(
                id = Uuid.random(),
                messages = listOf(
                    UIMessage(
                        role = MessageRole.ASSISTANT,
                        parts = listOf(UIMessagePart.Text("Hi there!")),
                        modelId = Uuid.random(),
                        usage = TokenUsage(promptTokens = 10, completionTokens = 5),
                    )
                ),
                selectIndex = 0,
            )
        )
        val nodesJson = JsonInstant.encodeToString(messageNodes)

        helper.createDatabase(11).apply {
            insertConversation(conversationId, "Test Conversation", nodesJson)
            close()
        }

        val db = helper.runMigrationsAndValidate(12, listOf(Migration_11_12))
        val nodes = db.queryNodes(conversationId)

        assertEquals("Should have migrated 2 message nodes", 2, nodes.size)

        val firstNode = nodes[0]
        assertNotNull("First node should have ID", firstNode.id)
        assertEquals("Conversation ID should match", conversationId, firstNode.conversationId)
        assertEquals("First node index should be 0", 0, firstNode.nodeIndex)
        assertEquals("First node selectIndex should be 0", 0, firstNode.selectIndex)

        val firstMessages = JsonInstant.decodeFromString<List<UIMessage>>(firstNode.messages)
        assertEquals("First node should have 1 message", 1, firstMessages.size)
        assertEquals("First message should be from USER", MessageRole.USER, firstMessages[0].role)
        assertEquals(
            "First message content should match",
            "Hello",
            (firstMessages[0].parts[0] as UIMessagePart.Text).text,
        )

        val secondNode = nodes[1]
        assertEquals("Second node index should be 1", 1, secondNode.nodeIndex)
        val secondMessages = JsonInstant.decodeFromString<List<UIMessage>>(secondNode.messages)
        assertEquals("Second node should have 1 message", 1, secondMessages.size)
        assertEquals("Second message should be from ASSISTANT", MessageRole.ASSISTANT, secondMessages[0].role)
        assertEquals(
            "Second message content should match",
            "Hi there!",
            (secondMessages[0].parts[0] as UIMessagePart.Text).text,
        )

        assertEquals(
            "Original nodes should be cleared to empty array",
            "[]",
            db.queryText("SELECT nodes FROM conversationentity WHERE id = ?", conversationId),
        )

        db.close()
    }

    @Test
    fun migrate11To12_handlesBranchedMessages() = runBlocking {
        val conversationId = Uuid.random().toString()
        val messageNodes = listOf(
            MessageNode(
                id = Uuid.random(),
                messages = listOf(
                    assistantMessage("Response 1"),
                    assistantMessage("Response 2"),
                    assistantMessage("Response 3"),
                ),
                selectIndex = 1,
            )
        )

        helper.createDatabase(11).apply {
            insertConversation(conversationId, "Branched Conversation", JsonInstant.encodeToString(messageNodes))
            close()
        }

        val db = helper.runMigrationsAndValidate(12, listOf(Migration_11_12))
        val nodes = db.queryNodes(conversationId)

        assertEquals("Should have migrated 1 message node", 1, nodes.size)
        val messages = JsonInstant.decodeFromString<List<UIMessage>>(nodes.single().messages)
        assertEquals("Node should have 3 messages", 3, messages.size)
        assertEquals("selectIndex should be preserved", 1, nodes.single().selectIndex)
        assertEquals(
            "Should preserve all message variants",
            "Response 2",
            (messages[1].parts[0] as UIMessagePart.Text).text,
        )

        db.close()
    }

    @Test
    fun migrate11To12_handlesEmptyConversations() = runBlocking {
        val conversationId = Uuid.random().toString()
        helper.createDatabase(11).apply {
            insertConversation(conversationId, "Empty Conversation", "[]")
            close()
        }

        val db = helper.runMigrationsAndValidate(12, listOf(Migration_11_12))
        assertEquals("Empty conversation should have no message nodes", 0, db.queryNodes(conversationId).size)
        assertEquals(
            "Conversation should still exist",
            1,
            db.rowCount("SELECT id FROM conversationentity WHERE id = ?", conversationId),
        )

        db.close()
    }

    @Test
    fun migrate11To12_handlesMultipleConversations() = runBlocking {
        val conversationId1 = Uuid.random().toString()
        val conversationId2 = Uuid.random().toString()
        val nodes1 = listOf(nodeWithUserText("Conversation 1"))
        val nodes2 = listOf(
            nodeWithUserText("Conversation 2 - Message 1"),
            nodeWithUserText("Conversation 2 - Message 2"),
        )

        helper.createDatabase(11).apply {
            insertConversation(conversationId1, "Conversation 1", JsonInstant.encodeToString(nodes1))
            insertConversation(conversationId2, "Conversation 2", JsonInstant.encodeToString(nodes2))
            close()
        }

        val db = helper.runMigrationsAndValidate(12, listOf(Migration_11_12))
        assertEquals("Conversation 1 should have 1 message node", 1, db.queryNodes(conversationId1).size)
        assertEquals("Conversation 2 should have 2 message nodes", 2, db.queryNodes(conversationId2).size)
        assertEquals("Total should have 3 message nodes", 3, db.rowCount("SELECT * FROM message_node"))

        db.close()
    }

    @Test
    fun migrate11To12_createsIndexOnConversationId() = runBlocking {
        helper.createDatabase(11).close()
        val db = helper.runMigrationsAndValidate(12, listOf(Migration_11_12))

        assertTrue(
            "Index on conversation_id should exist",
            db.rowCount(
                "SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='message_node' AND name='index_message_node_conversation_id'"
            ) > 0,
        )

        db.close()
    }

    @Test
    fun migrate11To12_handlesVeryLargeConversations() = runBlocking {
        val largeConversationId = Uuid.random().toString()
        val normalConversationId = Uuid.random().toString()
        val largeNodes = buildList {
            repeat(5000) { index ->
                add(
                    MessageNode(
                        id = Uuid.random(),
                        messages = listOf(
                            UIMessage(
                                role = MessageRole.USER,
                                parts = listOf(
                                    UIMessagePart.Text(
                                        "Message $index with some content to increase size " + "x".repeat(100)
                                    )
                                ),
                            ),
                            assistantMessage(
                                "Response $index with some content to increase size " + "y".repeat(100)
                            ),
                        ),
                        selectIndex = 0,
                    )
                )
            }
        }
        val normalNodes = listOf(nodeWithUserText("Normal conversation message"))

        helper.createDatabase(11).apply {
            insertConversation(largeConversationId, "Very Large Conversation", JsonInstant.encodeToString(largeNodes))
            insertConversation(normalConversationId, "Normal Conversation", JsonInstant.encodeToString(normalNodes))
            close()
        }

        val db = helper.runMigrationsAndValidate(12, listOf(Migration_11_12))
        val largeNodesMigrated = db.queryNodes(largeConversationId).size
        assertEquals("Normal conversation should be migrated successfully", 1, db.queryNodes(normalConversationId).size)
        assertEquals("Both conversations should still exist", 2, db.rowCount("SELECT id FROM conversationentity"))

        val largeConvNodes = db.queryText(
            "SELECT nodes FROM conversationentity WHERE id = ?",
            largeConversationId,
        )
        assertEquals(
            "Normal conversation nodes should be cleared",
            "[]",
            db.queryText("SELECT nodes FROM conversationentity WHERE id = ?", normalConversationId),
        )

        Log.i(
            "Migration_11_12_Test",
            "Large conversation migration result: $largeNodesMigrated nodes migrated, nodes field: ${if (largeConvNodes == "[]") "cleared" else "preserved"}",
        )

        db.close()
    }

    private fun assistantMessage(text: String) = UIMessage(
        role = MessageRole.ASSISTANT,
        parts = listOf(UIMessagePart.Text(text)),
        modelId = Uuid.random(),
    )

    private fun nodeWithUserText(text: String) = MessageNode(
        id = Uuid.random(),
        messages = listOf(
            UIMessage(
                role = MessageRole.USER,
                parts = listOf(UIMessagePart.Text(text)),
            )
        ),
        selectIndex = 0,
    )

    private suspend fun SQLiteConnection.insertConversation(id: String, title: String, nodes: String) {
        prepare(
            """
            INSERT INTO conversationentity(
                id, assistant_id, title, nodes, create_at, update_at, truncate_index, suggestions, is_pinned
            ) VALUES (?, ?, ?, ?, ?, ?, -1, '[]', 0)
            """.trimIndent()
        ).use { statement ->
            statement.bindText(1, id)
            statement.bindText(2, Uuid.random().toString())
            statement.bindText(3, title)
            statement.bindText(4, nodes)
            statement.bindLong(5, 1_700_000_000_000)
            statement.bindLong(6, 1_700_000_001_000)
            statement.step()
        }
    }

    private suspend fun SQLiteConnection.queryNodes(conversationId: String): List<NodeRow> =
        prepare(
            "SELECT id, conversation_id, node_index, messages, select_index " +
                "FROM message_node WHERE conversation_id = ? ORDER BY node_index ASC"
        ).use { statement ->
            statement.bindText(1, conversationId)
            buildList {
                while (statement.step()) {
                    add(
                        NodeRow(
                            id = statement.getText(0),
                            conversationId = statement.getText(1),
                            nodeIndex = statement.getInt(2),
                            messages = statement.getText(3),
                            selectIndex = statement.getInt(4),
                        )
                    )
                }
            }
        }

    private suspend fun SQLiteConnection.queryText(sql: String, argument: String): String =
        prepare(sql).use { statement ->
            statement.bindText(1, argument)
            check(statement.step())
            statement.getText(0)
        }

    private suspend fun SQLiteConnection.rowCount(sql: String, argument: String? = null): Int =
        prepare(sql).use { statement ->
            if (argument != null) statement.bindText(1, argument)
            var count = 0
            while (statement.step()) count++
            count
        }

    private data class NodeRow(
        val id: String,
        val conversationId: String,
        val nodeIndex: Int,
        val messages: String,
        val selectIndex: Int,
    )
}
