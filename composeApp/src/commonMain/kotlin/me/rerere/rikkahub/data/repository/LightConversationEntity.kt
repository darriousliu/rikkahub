package me.rerere.rikkahub.data.repository

/** 轻量级的会话查询结果，不包含 nodes 和 suggestions 字段。 */
data class LightConversationEntity(
    val id: String,
    val assistantId: String,
    val title: String,
    val isPinned: Boolean,
    val createAt: Long,
    val updateAt: Long,
    val folderId: String = "",
)
