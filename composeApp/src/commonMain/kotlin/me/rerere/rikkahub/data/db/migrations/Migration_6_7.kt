package me.rerere.rikkahub.data.db.migrations

import me.rerere.common.logging.RikkaLog as Log
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.db.DatabaseMigrationTracker
import me.rerere.rikkahub.data.db.execute
import me.rerere.rikkahub.data.db.forEachRow
import me.rerere.rikkahub.utils.JsonInstant

private const val TAG = "Migration_6_7"

val Migration_6_7 = object : Migration(6, 7) {
    override suspend fun migrate(connection: SQLiteConnection) {
        Log.i(TAG, "migrate: start migrate from 6 to 7")
        DatabaseMigrationTracker.onMigrationStart(6, 7)
        try {
            // 创建新表结构（不包含messages列）
            connection.execute(
                """
                CREATE TABLE ConversationEntity_new (
                    id TEXT NOT NULL PRIMARY KEY,
                    assistant_id TEXT NOT NULL DEFAULT '0950e2dc-9bd5-4801-afa3-aa887aa36b4e',
                    title TEXT NOT NULL,
                    nodes TEXT NOT NULL,
                    usage TEXT,
                    create_at INTEGER NOT NULL,
                    update_at INTEGER NOT NULL,
                    truncate_index INTEGER NOT NULL DEFAULT -1
                )
            """.trimIndent()
            )

            // 获取所有对话记录并转换数据
            val updates = mutableListOf<Array<Any?>>()

            connection.forEachRow(
                "SELECT id, assistant_id, title, messages, usage, create_at, update_at, truncate_index FROM ConversationEntity"
            ) { row ->
                val id = row.getText(0)
                val assistantId = row.getText(1)
                val title = row.getText(2)
                val messagesJson = row.getText(3)
                val usage = if (row.isNull(4)) null else row.getText(4)
                val createAt = row.getLong(5)
                val updateAt = row.getLong(6)
                val truncateIndex = row.getInt(7)

                try {
                    // 尝试解析旧格式的消息列表 List<UIMessage>
                    val oldMessages = JsonInstant.decodeFromString<List<UIMessage>>(messagesJson)

                    // 转换为新格式 List<MessageNode>
                    val newMessages = oldMessages.map { message ->
                        MessageNode.of(message)
                    }

                    // 序列化新格式
                    val newMessagesJson = JsonInstant.encodeToString(newMessages)
                    updates.add(
                        arrayOf(
                            id,
                            assistantId,
                            title,
                            newMessagesJson,
                            usage,
                            createAt,
                            updateAt,
                            truncateIndex
                        )
                    )
                } catch (e: Exception) {
                    // 如果解析失败，可能已经是新格式或者数据损坏，跳过
                    error("Failed to migrate messages for conversation $id: ${e.message}")
                }
            }

            // 批量插入数据到新表
            updates.forEach { values ->
                connection.execute(
                    "INSERT INTO ConversationEntity_new (id, assistant_id, title, nodes, usage, create_at, update_at, truncate_index) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    values.toList(),
                )
            }

            // 删除旧表
            connection.execute("DROP TABLE ConversationEntity")

            // 重命名新表
            connection.execute("ALTER TABLE ConversationEntity_new RENAME TO ConversationEntity")

            Log.i(TAG, "migrate: migrate from 6 to 7 success (${updates.size} conversations updated)")
        } finally {
            DatabaseMigrationTracker.onMigrationEnd()
        }
    }
}
