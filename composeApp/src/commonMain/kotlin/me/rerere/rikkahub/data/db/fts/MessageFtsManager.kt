package me.rerere.rikkahub.data.db.fts

import me.rerere.common.logging.RikkaLog as Log
import androidx.room3.PooledConnection
import androidx.room3.useReaderConnection
import androidx.room3.useWriterConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.async.step
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.model.Conversation
import kotlin.time.Instant

data class MessageSearchResult(
    val nodeId: String,
    val messageId: String,
    val conversationId: String,
    val title: String,
    val updateAt: Instant,
    val snippet: String,
)

enum class MessageSearchSort(val orderBy: String) {
    RELEVANCE("rank, update_at DESC"),
    NEWEST_FIRST("update_at DESC, rank"),
    OLDEST_FIRST("update_at ASC, rank"),
}

enum class MessageFtsDialect(
    internal val tokenizer: String,
    internal val queryExpression: String,
    internal val snippetExpression: String,
) {
    SIMPLE(
        tokenizer = "simple",
        queryExpression = "jieba_query(?)",
        snippetExpression = "simple_snippet(message_fts, 0, '[', ']', '...', 30)",
    ),
    UNICODE61(
        tokenizer = "unicode61",
        queryExpression = "?",
        snippetExpression = "snippet(message_fts, 0, '[', ']', '...', 30)",
    ),
}

private const val TAG = "MessageFtsManager"

class MessageFtsManager(
    private val database: AppDatabase,
    private val dialect: MessageFtsDialect,
) {

    suspend fun indexConversation(conversation: Conversation) = database.useWriterConnection { connection ->
        val conversationId = conversation.id.toString()
        connection.execute("DELETE FROM message_fts WHERE conversation_id = ?") {
            bindText(1, conversationId)
        }
        conversation.messageNodes.forEach { node ->
            node.messages.forEach { message ->
                val text = message.extractFtsText()
                if (text.isNotBlank()) {
                    connection.execute(
                        "INSERT INTO message_fts(text, node_id, message_id, conversation_id, title, update_at) VALUES (?, ?, ?, ?, ?, ?)"
                    ) {
                        bindText(1, text)
                        bindText(2, node.id.toString())
                        bindText(3, message.id.toString())
                        bindText(4, conversationId)
                        bindText(5, conversation.title)
                        bindLong(6, conversation.updateAt.toEpochMilliseconds())
                    }
                }
            }
        }
    }

    suspend fun deleteConversation(conversationId: String) = database.useWriterConnection { connection ->
        connection.execute("DELETE FROM message_fts WHERE conversation_id = ?") {
            bindText(1, conversationId)
        }
    }

    suspend fun deleteAll() = database.useWriterConnection { connection ->
        connection.execute("DELETE FROM message_fts")
    }

    suspend fun search(
        keyword: String,
        sort: MessageSearchSort = MessageSearchSort.RELEVANCE,
    ): List<MessageSearchResult> = database.useReaderConnection { connection ->
        connection.usePrepared(
            """
            SELECT node_id, message_id, conversation_id, title, update_at,
                   ${dialect.snippetExpression} AS snippet
            FROM message_fts
            WHERE text MATCH ${dialect.queryExpression}
            ORDER BY ${sort.orderBy}
            LIMIT 50
            """.trimIndent()
        ) { statement ->
            val results = mutableListOf<MessageSearchResult>()
            statement.bindText(1, keyword)
            Log.i(TAG, "search: $keyword")
            while (statement.step()) {
                results += MessageSearchResult(
                    nodeId = statement.getText(0),
                    messageId = statement.getText(1),
                    conversationId = statement.getText(2),
                    title = statement.getText(3),
                    updateAt = Instant.fromEpochMilliseconds(statement.getLong(4)),
                    snippet = statement.getText(5),
                )
            }
            results
        }
    }
}

private suspend fun PooledConnection.execute(
    sql: String,
    bind: SQLiteStatement.() -> Unit = {},
) {
    usePrepared(sql) { statement ->
        statement.bind()
        statement.step()
    }
}

private fun UIMessage.extractFtsText(): String =
    parts.filterIsInstance<UIMessagePart.Text>()
        .joinToString("\n") { it.text }
        .take(10_000)
