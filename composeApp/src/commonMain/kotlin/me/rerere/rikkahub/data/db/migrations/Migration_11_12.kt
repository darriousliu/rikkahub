package me.rerere.rikkahub.data.db.migrations

import me.rerere.common.logging.RikkaLog as Log
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.rikkahub.data.db.DatabaseMigrationTracker
import me.rerere.rikkahub.data.db.execute
import me.rerere.rikkahub.data.db.forEachRow
import me.rerere.rikkahub.utils.JsonInstant
import kotlin.uuid.Uuid

private const val TAG = "Migration_11_12"

val Migration_11_12 = object : Migration(11, 12) {
    override suspend fun migrate(connection: SQLiteConnection) {
        Log.i(TAG, "migrate: start migrate from 11 to 12 (extracting message nodes to separate table)")
        DatabaseMigrationTracker.onMigrationStart(11, 12)
        try {
            // 1. 创建 message_node 表
            connection.execute(
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
            connection.execute("CREATE INDEX IF NOT EXISTS index_message_node_conversation_id ON message_node(conversation_id)")

            // 2. 从 conversationentity.nodes 迁移数据到 message_node
            val conversationIds = mutableListOf<String>()
            connection.forEachRow("SELECT id FROM conversationentity") { row ->
                conversationIds += row.getText(0)
            }
            var migratedCount = 0
            var nodeCount = 0
            var skippedCount = 0

            for (conversationId in conversationIds) {
                var nodesJson: String? = null
                connection.forEachRow(
                    "SELECT nodes FROM conversationentity WHERE id = ?",
                    listOf(conversationId),
                ) { row ->
                    nodesJson = row.getText(0)
                }
                val storedNodes = nodesJson ?: continue

                // 使用原始 JSON 解析，避免因 UIMessagePart 类型名变更导致的反序列化失败
                // 同时应用类型名映射（与 Migration_13_14 相同的逻辑）
                val nodesArray = runCatching {
                    JsonInstant.parseToJsonElement(storedNodes) as? JsonArray
                }.getOrNull() ?: JsonArray(emptyList())

                nodesArray.forEachIndexed { index, nodeElement ->
                    val nodeObject = nodeElement as? JsonObject ?: return@forEachIndexed
                    val messagesElement = nodeObject["messages"] ?: JsonArray(emptyList())
                    // 迁移消息中的 UIMessagePart 类型名（旧完整类名 -> 新 @SerialName）
                    val migratedMessages = migrateMessagesElement(messagesElement)
                    val messagesJson = JsonInstant.encodeToString(migratedMessages)
                    val selectIndex = runCatching {
                        nodeObject["selectIndex"]?.jsonPrimitive?.int ?: 0
                    }.getOrDefault(0)
                    val nodeId = Uuid.random().toString()
                    connection.execute(
                        "INSERT INTO message_node (id, conversation_id, node_index, messages, select_index) VALUES (?, ?, ?, ?, ?)",
                        listOf(nodeId, conversationId, index, messagesJson, selectIndex),
                    )
                    nodeCount++
                }
                connection.execute(
                    "UPDATE conversationentity SET nodes = '[]' WHERE id = ?",
                    listOf(conversationId),
                )
                migratedCount++
            }

            Log.i(
                TAG,
                "migrate: migrate from 11 to 12 success ($migratedCount conversations, $nodeCount nodes, $skippedCount skipped)"
            )
        } finally {
            DatabaseMigrationTracker.onMigrationEnd()
        }
    }
}
