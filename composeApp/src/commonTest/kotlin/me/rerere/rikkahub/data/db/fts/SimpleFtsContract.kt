package me.rerere.rikkahub.data.db.fts

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteException
import androidx.sqlite.async.prepare
import androidx.sqlite.async.step
import me.rerere.rikkahub.data.db.createMessageFts
import me.rerere.rikkahub.data.db.execute
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private const val text = "中华人民共和国国歌 南京市长江大桥 Kotlin Compose"

internal suspend fun verifySimpleQueries(driver: SQLiteDriver) {
    driver.open(":memory:").use { connection ->
        connection.execute("SELECT jieba_dict(?)", listOf(SimpleDictManager.extractDict().toString()))
        createMessageFts(connection, MessageFtsDialect.SIMPLE)
        connection.insertFixture()
        for ((query, match) in listOf(
            "中华" to "[中华]人民共和国国歌",
            "中华国歌" to "[中华]人民共和国[国歌]",
            "nanjing" to "[南京]市长江大桥",
            "长江大桥" to "南京市[长江大桥]",
            "kotlin" to "[Kotlin] Compose",
        )) {
            val snippet = connection.querySnippet(query)
            assertTrue(snippet.contains(match), "$query: $snippet")
        }
        // Opening an existing Simple table must leave its rows and rowids intact.
        createMessageFts(connection, MessageFtsDialect.SIMPLE)
        connection.assertFixture()
    }
}

internal suspend fun verifyUnicodeMigration(driver: SQLiteDriver) {
    driver.open(":memory:").use { connection ->
        connection.execute("SELECT jieba_dict(?)", listOf(SimpleDictManager.extractDict().toString()))
        createMessageFts(connection, MessageFtsDialect.UNICODE61)
        connection.insertFixture()
        assertEquals(0L, connection.scalar("SELECT count(*) FROM message_fts WHERE text MATCH '国歌'"))

        createMessageFts(connection, MessageFtsDialect.SIMPLE)
        connection.assertFixture()
        assertTrue(connection.querySnippet("国歌").contains("[国歌]"))
        // A second open must not duplicate or reset the converted index.
        createMessageFts(connection, MessageFtsDialect.SIMPLE)
        connection.assertFixture()
    }
}

internal suspend fun verifyMigrationRollback(driverWithoutSimple: SQLiteDriver) {
    driverWithoutSimple.open(":memory:").use { connection ->
        createMessageFts(connection, MessageFtsDialect.UNICODE61)
        connection.insertFixture()
        assertFailsWith<SQLiteException> { createMessageFts(connection, MessageFtsDialect.SIMPLE) }
        connection.assertFixture()
        assertEquals(0L, connection.scalar("SELECT count(*) FROM sqlite_master WHERE name='message_fts_simple'"))
        // The failed conversion must also release its savepoint.
        connection.execute("BEGIN")
        connection.execute("ROLLBACK")
    }
}

private suspend fun SQLiteConnection.insertFixture() = execute(
    "INSERT INTO message_fts(rowid,text,node_id,message_id,conversation_id,title,update_at) VALUES(?,?,?,?,?,?,?)",
    listOf(42L, text, "node", "message", "conversation", "原始标题's", 1234567890123L),
)

private suspend fun SQLiteConnection.assertFixture() {
    assertEquals(1L, scalar("SELECT count(*) FROM message_fts"))
    prepare("SELECT rowid,text,node_id,message_id,conversation_id,title,update_at FROM message_fts").use {
        assertTrue(it.step())
        assertEquals(42L, it.getLong(0))
        assertEquals(text, it.getText(1))
        assertEquals("node", it.getText(2))
        assertEquals("message", it.getText(3))
        assertEquals("conversation", it.getText(4))
        assertEquals("原始标题's", it.getText(5))
        assertEquals(1234567890123L, it.getLong(6))
    }
}

private suspend fun SQLiteConnection.scalar(sql: String): Long = prepare(sql).use {
    assertTrue(it.step())
    it.getLong(0)
}

private suspend fun SQLiteConnection.querySnippet(query: String): String = prepare(
    "SELECT simple_snippet(message_fts,0,'[',']','...',30) FROM message_fts WHERE text MATCH jieba_query(?)"
).use {
    it.bindText(1, query)
    assertTrue(it.step(), "No match for $query")
    it.getText(0)
}
