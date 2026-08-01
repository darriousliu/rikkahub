package me.rerere.rikkahub.data.db.migrations

import me.rerere.common.logging.RikkaLog as Log
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.migrateToolNodes
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.data.db.DatabaseMigrationTracker
import me.rerere.rikkahub.data.db.execute
import me.rerere.rikkahub.data.db.forEachRow

private const val TAG = "Migration_15_16"

val Migration_15_16 = object : Migration(15, 16) {
    override suspend fun migrate(connection: SQLiteConnection) {
        Log.i(TAG, "migrate: start migrate from 15 to 16 (eager tool message migration)")
        DatabaseMigrationTracker.onMigrationStart(15, 16)
        try {
            data class NodeRow(val id: String, val messages: List<UIMessage>, val selectIndex: Int)

            // Get all distinct conversation IDs
            val conversationIds = mutableListOf<String>()
            connection.forEachRow("SELECT DISTINCT conversation_id FROM message_node") { row ->
                conversationIds.add(row.getText(0))
            }

            var updatedConversations = 0

            for (conversationId in conversationIds) {
                // Load all nodes for this conversation ordered by node_index
                val rows = mutableListOf<NodeRow>()
                connection.forEachRow(
                    "SELECT id, messages, node_index, select_index FROM message_node WHERE conversation_id = ? ORDER BY node_index ASC",
                    listOf(conversationId),
                ) { row ->
                    val id = row.getText(0)
                    val messagesJson = row.getText(1)
                    val selectIndex = row.getInt(3)
                    runCatching {
                        val messages = JsonInstant.decodeFromString<List<UIMessage>>(messagesJson)
                        rows.add(NodeRow(id, messages, selectIndex))
                    }.onFailure {
                        Log.w(TAG, "migrate: failed to parse messages for node $id", it)
                    }
                }

                if (rows.isEmpty()) continue

                // Apply migration: merge TOOL role nodes into preceding ASSISTANT nodes,
                // and convert legacy ToolCall/ToolResult parts to the unified Tool part
                val migrated = rows.migrateToolNodes(
                    getMessages = { it.messages },
                    setMessages = { row, msgs -> row.copy(messages = msgs) }
                )

                // Skip if nothing changed
                val changed = migrated.size != rows.size ||
                    migrated.zip(rows).any { (a, b) -> a.messages != b.messages }
                if (!changed) continue

                // Delete old nodes and re-insert migrated ones with corrected node_index
                connection.execute(
                    "DELETE FROM message_node WHERE conversation_id = ?",
                    listOf(conversationId),
                )
                migrated.forEachIndexed { index, row ->
                    val messagesJson = JsonInstant.encodeToString(row.messages)
                    connection.execute(
                        "INSERT INTO message_node (id, conversation_id, node_index, messages, select_index) VALUES (?, ?, ?, ?, ?)",
                        listOf(row.id, conversationId, index, messagesJson, row.selectIndex),
                    )
                }
                updatedConversations++
            }

            Log.i(TAG, "migrate: migrate from 15 to 16 success ($updatedConversations conversations updated)")
        } finally {
            DatabaseMigrationTracker.onMigrationEnd()
        }
    }
}
