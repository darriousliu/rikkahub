package me.rerere.rikkahub.data.db.migrations

import me.rerere.common.logging.RikkaLog as Log
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import me.rerere.rikkahub.data.db.DatabaseMigrationTracker
import me.rerere.rikkahub.data.db.execute
import me.rerere.rikkahub.data.db.forEachRow

private const val TAG = "Migration_13_14"

val Migration_13_14 = object : Migration(13, 14) {
    override suspend fun migrate(connection: SQLiteConnection) {
        Log.i(TAG, "migrate: start migrate from 13 to 14 (UIMessagePart type -> @SerialName)")
        DatabaseMigrationTracker.onMigrationStart(13, 14)
        try {
            var updatedCount = 0
            connection.forEachRow("SELECT id, messages FROM message_node") { row ->
                val id = row.getText(0)
                val messagesJson = row.getText(1)
                val migratedJson = migrateMessagesJson(messagesJson)
                if (migratedJson != messagesJson) {
                    connection.execute(
                        "UPDATE message_node SET messages = ? WHERE id = ?",
                        listOf(migratedJson, id),
                    )
                    updatedCount++
                }
            }
            Log.i(TAG, "migrate: migrate from 13 to 14 success ($updatedCount nodes updated)")
        } finally {
            DatabaseMigrationTracker.onMigrationEnd()
        }
    }
}
