package me.rerere.rikkahub.data.db.migrations

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import me.rerere.rikkahub.data.db.DatabaseMigrationTracker
import me.rerere.rikkahub.data.db.execute

val Migration_14_15 = object : Migration(14, 15) {
    override suspend fun migrate(connection: SQLiteConnection) {
        DatabaseMigrationTracker.onMigrationStart(14, 15)
        try {
            connection.execute(
                """
            CREATE TABLE IF NOT EXISTS favorites (
                id TEXT NOT NULL PRIMARY KEY,
                type TEXT NOT NULL,
                ref_key TEXT NOT NULL,
                ref_json TEXT NOT NULL,
                snapshot_json TEXT NOT NULL,
                meta_json TEXT,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
            )
            connection.execute("CREATE UNIQUE INDEX IF NOT EXISTS index_favorites_ref_key ON favorites(ref_key)")
            connection.execute("CREATE INDEX IF NOT EXISTS index_favorites_type ON favorites(type)")
            connection.execute("CREATE INDEX IF NOT EXISTS index_favorites_created_at ON favorites(created_at)")
        } finally {
            DatabaseMigrationTracker.onMigrationEnd()
        }
    }
}
