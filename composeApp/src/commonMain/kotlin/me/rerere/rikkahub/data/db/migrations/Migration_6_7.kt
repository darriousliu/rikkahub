package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import co.touchlab.kermit.Logger
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.utils.JsonInstant

private const val TAG = "Migration_6_7"

val Migration_6_7 = object : Migration(6, 7) {
    override fun migrate(connection: SQLiteConnection) {
        Logger.i(TAG) { "start migrate from 6 to 7" }

        // 1. 创建新表结构（不包含 messages 列）
        connection.execSQL(
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

        // 2. 读取所有旧数据
        data class Row(
            val id: String,
            val assistantId: String,
            val title: String,
            val messagesJson: String,
            val usage: String?,
            val createAt: Long,
            val updateAt: Long,
            val truncateIndex: Long,
        )

        val rows = mutableListOf<Row>()
        connection.prepare(
            "SELECT id, assistant_id, title, messages, usage, create_at, update_at, truncate_index FROM ConversationEntity"
        ).use { stmt ->
            while (stmt.step()) {
                rows.add(
                    Row(
                        id = stmt.getText(0),
                        assistantId = stmt.getText(1),
                        title = stmt.getText(2),
                        messagesJson = stmt.getText(3),
                        usage = if (stmt.isNull(4)) null else stmt.getText(4),
                        createAt = stmt.getLong(5),
                        updateAt = stmt.getLong(6),
                        truncateIndex = stmt.getLong(7),
                    )
                )
            }
        }

        // 3. 转换并插入新表
        var migratedCount = 0
        connection.prepare(
            "INSERT INTO ConversationEntity_new (id, assistant_id, title, nodes, usage, create_at, update_at, truncate_index) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
        ).use { insertStmt ->
            for (row in rows) {
                try {
                    val oldMessages = JsonInstant.decodeFromString<List<UIMessage>>(row.messagesJson)
                    val newNodes = oldMessages.map { MessageNode.of(it) }
                    val nodesJson = JsonInstant.encodeToString(newNodes)

                    insertStmt.bindText(1, row.id)
                    insertStmt.bindText(2, row.assistantId)
                    insertStmt.bindText(3, row.title)
                    insertStmt.bindText(4, nodesJson)
                    if (row.usage != null) {
                        insertStmt.bindText(5, row.usage)
                    } else {
                        insertStmt.bindNull(5)
                    }
                    insertStmt.bindLong(6, row.createAt)
                    insertStmt.bindLong(7, row.updateAt)
                    insertStmt.bindLong(8, row.truncateIndex)
                    insertStmt.step()
                    insertStmt.reset()
                    migratedCount++
                } catch (e: Exception) {
                    error("Failed to migrate messages for conversation ${row.id}: ${e.message}")
                }
            }
        }

        // 4. 替换旧表
        connection.execSQL("DROP TABLE ConversationEntity")
        connection.execSQL("ALTER TABLE ConversationEntity_new RENAME TO ConversationEntity")

        Logger.i(TAG) { "migrate 6→7 success ($migratedCount conversations updated)" }
    }
}
