package me.rerere.rikkahub.data.db

import androidx.room3.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import me.rerere.rikkahub.data.db.fts.MessageFtsDialect
import me.rerere.rikkahub.data.db.migrations.Migration_11_12
import me.rerere.rikkahub.data.db.migrations.Migration_13_14
import me.rerere.rikkahub.data.db.migrations.Migration_14_15
import me.rerere.rikkahub.data.db.migrations.Migration_15_16
import me.rerere.rikkahub.data.db.migrations.Migration_6_7

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
            connection.execute(
                """
                CREATE VIRTUAL TABLE IF NOT EXISTS message_fts USING fts5(
                    text,
                    node_id UNINDEXED,
                    message_id UNINDEXED,
                    conversation_id UNINDEXED,
                    title UNINDEXED,
                    update_at UNINDEXED,
                    tokenize = '${ftsDialect.tokenizer}'
                )
                """.trimIndent()
            )
        }
    })
    .build()
