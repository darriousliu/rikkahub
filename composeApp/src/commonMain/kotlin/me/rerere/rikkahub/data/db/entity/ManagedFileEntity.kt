package me.rerere.rikkahub.data.db.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "managed_files",
    indices = [
        Index(value = ["relative_path"], unique = true),
        Index(value = ["folder"])
    ]
)
data class ManagedFileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo("folder")
    val folder: String,
    @ColumnInfo("relative_path")
    val relativePath: String,
    @ColumnInfo("display_name")
    val displayName: String,
    @ColumnInfo("mime_type")
    val mimeType: String,
    @ColumnInfo("size_bytes")
    val sizeBytes: Long,
    @ColumnInfo("created_at")
    val createdAt: Long,
    @ColumnInfo("updated_at")
    val updatedAt: Long,
)
