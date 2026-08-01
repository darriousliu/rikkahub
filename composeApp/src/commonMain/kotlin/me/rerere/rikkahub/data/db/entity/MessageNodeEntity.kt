package me.rerere.rikkahub.data.db.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "message_node",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversation_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("conversation_id")]
)
data class MessageNodeEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo("conversation_id")
    val conversationId: String,
    @ColumnInfo("node_index")
    val nodeIndex: Int,
    @ColumnInfo("messages")
    val messages: String,  // JSON serialized List<UIMessage>
    @ColumnInfo("select_index")
    val selectIndex: Int
)
