package me.rerere.rikkahub.data.db

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.async.prepare
import androidx.sqlite.async.step

internal suspend fun SQLiteConnection.execute(
    sql: String,
    arguments: List<Any?> = emptyList(),
) {
    prepare(sql).use { statement ->
        statement.bindArguments(arguments)
        statement.step()
    }
}

internal suspend fun SQLiteConnection.forEachRow(
    sql: String,
    arguments: List<Any?> = emptyList(),
    block: suspend (SQLiteStatement) -> Unit,
) {
    prepare(sql).use { statement ->
        statement.bindArguments(arguments)
        while (statement.step()) block(statement)
    }
}

private fun SQLiteStatement.bindArguments(arguments: List<Any?>) {
    arguments.forEachIndexed { index, value ->
        val bindingIndex = index + 1
        when (value) {
            null -> bindNull(bindingIndex)
            is ByteArray -> bindBlob(bindingIndex, value)
            is Boolean -> bindBoolean(bindingIndex, value)
            is Double -> bindDouble(bindingIndex, value)
            is Float -> bindFloat(bindingIndex, value)
            is Int -> bindInt(bindingIndex, value)
            is Long -> bindLong(bindingIndex, value)
            is String -> bindText(bindingIndex, value)
            else -> error("Unsupported SQLite argument type: ${value::class}")
        }
    }
}
