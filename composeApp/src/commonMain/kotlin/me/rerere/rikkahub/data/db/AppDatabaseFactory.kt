package me.rerere.rikkahub.data.db

import androidx.room3.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.async.prepare
import androidx.sqlite.async.step
import me.rerere.rikkahub.data.db.fts.MessageFtsDialect
import me.rerere.rikkahub.data.db.fts.SimpleDictManager
import me.rerere.rikkahub.data.db.migrations.Migration_11_12
import me.rerere.rikkahub.data.db.migrations.Migration_13_14
import me.rerere.rikkahub.data.db.migrations.Migration_14_15
import me.rerere.rikkahub.data.db.migrations.Migration_15_16
import me.rerere.rikkahub.data.db.migrations.Migration_6_7
import me.rerere.common.logging.RikkaLog as Log

fun buildAppDatabase(
    builder: RoomDatabase.Builder<AppDatabase>,
    driver: SQLiteDriver,
    ftsDialect: MessageFtsDialect,
    platformOnOpen: suspend (SQLiteConnection) -> Unit = {},
): AppDatabase = builder
    .setDriver(driver)
    .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
    .addMigrations(Migration_6_7, Migration_11_12, Migration_13_14, Migration_14_15, Migration_15_16)
    .addCallback(object : RoomDatabase.Callback() {
        override suspend fun onOpen(connection: SQLiteConnection) {
            platformOnOpen(connection)
            if (ftsDialect == MessageFtsDialect.SIMPLE) {
                val dictDir = SimpleDictManager.extractDict().toString()
                connection.prepare("SELECT jieba_dict(?)").use { statement ->
                    statement.bindText(1, dictDir)
                    if (statement.step()) {
                        val result = statement.getText(0)
                        if (result.trimEnd('/', '\\') != dictDir.trimEnd('/', '\\')) {
                            Log.e("DataSourceModule", "jieba_dict failed: $result, path=$dictDir")
                        }
                    }
                }
            }
            createMessageFts(connection, ftsDialect)
        }
    })
    .build()

internal suspend fun createMessageFts(connection: SQLiteConnection, dialect: MessageFtsDialect) {
    val createSql = """
        CREATE VIRTUAL TABLE IF NOT EXISTS message_fts USING fts5(
            text,
            node_id UNINDEXED,
            message_id UNINDEXED,
            conversation_id UNINDEXED,
            title UNINDEXED,
            update_at UNINDEXED,
            tokenize = '${dialect.tokenizer}'
        )
        """.trimIndent()
    val previousSql = connection.prepare("SELECT sql FROM sqlite_master WHERE name = 'message_fts'").use {
        if (it.step()) it.getText(0) else null
    }
    if (dialect == MessageFtsDialect.SIMPLE && previousSql?.contains("unicode61", ignoreCase = true) == true) {
        // 旧 iOS/JVM 表存有原文。只转换派生索引，保留行号、消息关联和时间；失败时回滚。
        connection.execute("SAVEPOINT migrate_message_fts")
        try {
            connection.execute(createSql.replace("message_fts", "message_fts_simple"))
            connection.execute(
                "INSERT INTO message_fts_simple(rowid, text, node_id, message_id, conversation_id, title, update_at) " +
                    "SELECT rowid, text, node_id, message_id, conversation_id, title, update_at FROM message_fts"
            )
            connection.execute("DROP TABLE message_fts")
            connection.execute("ALTER TABLE message_fts_simple RENAME TO message_fts")
            connection.execute("RELEASE migrate_message_fts")
        } catch (error: Throwable) {
            connection.execute("ROLLBACK TO migrate_message_fts")
            connection.execute("RELEASE migrate_message_fts")
            throw error
        }
    } else {
        connection.execute(createSql)
    }
}
