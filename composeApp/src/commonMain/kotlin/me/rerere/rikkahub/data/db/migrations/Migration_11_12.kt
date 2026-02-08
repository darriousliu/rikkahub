package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteException
import androidx.sqlite.execSQL
import co.touchlab.kermit.Logger
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.utils.JsonInstant
import kotlin.uuid.Uuid

private const val TAG = "Migration_11_12"

val Migration_11_12 = object : Migration(11, 12) {
    override fun migrate(connection: SQLiteConnection) {
        Logger.i(TAG) { "migrate: start migrate from 11 to 12 (extracting message nodes to separate table)" }
        // 1. 创建 message_node 表
        connection.execSQL(
            """
                CREATE TABLE IF NOT EXISTS message_node (
                    id TEXT NOT NULL PRIMARY KEY,
                    conversation_id TEXT NOT NULL,
                    node_index INTEGER NOT NULL,
                    messages TEXT NOT NULL,
                    select_index INTEGER NOT NULL,
                    FOREIGN KEY (conversation_id) REFERENCES ConversationEntity(id) ON DELETE CASCADE
                )
                """.trimIndent()
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_message_node_conversation_id ON message_node(conversation_id)")

        // 2. 读取所有 conversation id
        val conversationIds = mutableListOf<String>()
        connection.prepare("SELECT id FROM conversationentity").use { stmt ->
            while (stmt.step()) {
                conversationIds.add(stmt.getText(0))
            }
        }

        var migratedCount = 0
        var nodeCount = 0
        var skippedCount = 0

        // 3. 逐条迁移 nodes
        for (conversationId in conversationIds) {
            try {
                var nodesJson: String? = null
                connection.prepare(
                    "SELECT nodes FROM conversationentity WHERE id = ?"
                ).use { stmt ->
                    stmt.bindText(1, conversationId)
                    if (stmt.step()) {
                        nodesJson = stmt.getText(0)
                    }
                }
                val json = nodesJson ?: continue
                val nodes = JsonInstant.decodeFromString<List<MessageNode>>(json)
                connection.prepare(
                    "INSERT INTO message_node (id, conversation_id, node_index, messages, select_index) VALUES (?, ?, ?, ?, ?)"
                ).use { insertStmt ->
                    nodes.forEachIndexed { index, node ->
                        val nodeId = Uuid.random().toString()
                        val messagesJson = JsonInstant.encodeToString(node.messages)
                        insertStmt.bindText(1, nodeId)
                        insertStmt.bindText(2, conversationId)
                        insertStmt.bindInt(3, index)
                        insertStmt.bindText(4, messagesJson)
                        insertStmt.bindInt(5, node.selectIndex)
                        insertStmt.step()
                        insertStmt.reset() // 重置以复用 prepared statement
                        nodeCount++
                    }
                }
                // 清空原 nodes 字段
                connection.prepare(
                    "UPDATE conversationentity SET nodes = '[]' WHERE id = ?"
                ).use { updateStmt ->
                    updateStmt.bindText(1, conversationId)
                    updateStmt.step()
                }
                migratedCount++
            } catch (e: SQLiteException) {
                // KMP 没有 SQLiteBlobTooBigException，用通用异常捕获
                skippedCount++
                Logger.w(TAG) { "skip conversation $conversationId: ${e.message}" }
            }
        }

        connection.execSQL("END TRANSACTION")
        Logger.i(TAG) {
            "migrate: migrate from 11 to 12 success ($migratedCount conversations, $nodeCount nodes, $skippedCount skipped)"
        }
    }
}
