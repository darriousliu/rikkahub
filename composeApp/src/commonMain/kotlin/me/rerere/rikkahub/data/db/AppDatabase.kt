package me.rerere.rikkahub.data.db

import androidx.room.AutoMigration
import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import co.touchlab.kermit.Logger
import me.rerere.ai.core.TokenUsage
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.db.dao.ConversationDAO
import me.rerere.rikkahub.data.db.dao.GenMediaDAO
import me.rerere.rikkahub.data.db.dao.MemoryDAO
import me.rerere.rikkahub.data.db.entity.ConversationEntity
import me.rerere.rikkahub.data.db.entity.GenMediaEntity
import me.rerere.rikkahub.data.db.entity.MemoryEntity
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.utils.JsonInstant

private const val TAG = "AppDatabase"

@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

@ConstructedBy(AppDatabaseConstructor::class)
@Database(
    entities = [ConversationEntity::class, MemoryEntity::class, GenMediaEntity::class],
    version = 11,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 7, to = 8),
        AutoMigration(from = 8, to = 9, spec = Migration_8_9::class),
        AutoMigration(from = 9, to = 10),
        AutoMigration(from = 10, to = 11),
    ]
)
@TypeConverters(TokenUsageConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDAO

    abstract fun memoryDao(): MemoryDAO

    abstract fun genMediaDao(): GenMediaDAO
}

object TokenUsageConverter {
    @TypeConverter
    fun fromTokenUsage(usage: TokenUsage?): String {
        return JsonInstant.encodeToString(usage)
    }

    @TypeConverter
    fun toTokenUsage(usage: String): TokenUsage? {
        return JsonInstant.decodeFromString(usage)
    }
}

val Migration_6_7 = object : Migration(6, 7) {
    override fun migrate(connection: SQLiteConnection) {
        Logger.i(TAG) { "migrate: start migrate from 6 to 7" }
        connection.execSQL("BEGIN IMMEDIATE TRANSACTION")
        try {
            // 创建新表结构（不包含messages列）
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

            // 获取所有对话记录并转换数据
            val updates = mutableListOf<Array<Any?>>()

            connection.prepare("SELECT id, assistant_id, title, messages, usage, create_at, update_at, truncate_index FROM ConversationEntity")
                .use { statement ->
                    while (statement.step()) {
                        val id = statement.getText(0)
                        val assistantId = statement.getText(1)
                        val title = statement.getText(2)
                        val messagesJson = statement.getText(3)
                        val usage = statement.getText(4)
                        val createAt = statement.getLong(5)
                        val updateAt = statement.getLong(6)
                        val truncateIndex = statement.getInt(7)

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
                    statement.close()
                }


            // 批量插入数据到新表
            updates.forEach { values ->
                connection.prepare(
                    "INSERT INTO ConversationEntity_new (id, assistant_id, title, nodes, usage, create_at, update_at, truncate_index) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                ).use { statement ->
                    statement.bindText(1, values[0] as String)
                    statement.bindText(2, values[1] as String)
                    statement.bindText(3, values[2] as String)
                    statement.bindText(4, values[3] as String)
                    statement.bindText(5, values[4] as String)
                    statement.bindLong(6, values[5] as Long)
                    statement.bindLong(7, values[6] as Long)
                    statement.bindLong(8, values[7] as Long)
                    statement.step()
                }
            }

            // 删除旧表
            connection.execSQL("DROP TABLE ConversationEntity")

            // 重命名新表
            connection.execSQL("ALTER TABLE ConversationEntity_new RENAME TO ConversationEntity")

            connection.execSQL("END TRANSACTION")

            Logger.i(TAG) { "migrate: migrate from 6 to 7 success (${updates.size} conversations updated)" }
        } catch (t: Throwable) {
            connection.execSQL("ROLLBACK TRANSACTION")
        }
    }
}

@DeleteColumn(tableName = "ConversationEntity", columnName = "usage")
class Migration_8_9 : AutoMigrationSpec
