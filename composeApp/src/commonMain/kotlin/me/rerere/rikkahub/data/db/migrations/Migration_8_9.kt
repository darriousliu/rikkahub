package me.rerere.rikkahub.data.db.migrations

import androidx.room3.DeleteColumn
import androidx.room3.migration.AutoMigrationSpec

@DeleteColumn(tableName = "ConversationEntity", columnName = "usage")
class Migration_8_9 : AutoMigrationSpec
