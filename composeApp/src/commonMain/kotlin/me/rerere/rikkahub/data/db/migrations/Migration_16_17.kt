package me.rerere.rikkahub.data.db.migrations

import androidx.room3.DeleteColumn
import androidx.room3.migration.AutoMigrationSpec

@DeleteColumn(tableName = "ConversationEntity", columnName = "truncate_index")
class Migration_16_17 : AutoMigrationSpec
