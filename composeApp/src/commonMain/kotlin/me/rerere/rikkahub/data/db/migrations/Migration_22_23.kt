package me.rerere.rikkahub.data.db.migrations

import androidx.room3.DeleteColumn
import androidx.room3.migration.AutoMigrationSpec

@DeleteColumn(tableName = "workspaces", columnName = "shell_enabled")
class Migration_22_23 : AutoMigrationSpec
