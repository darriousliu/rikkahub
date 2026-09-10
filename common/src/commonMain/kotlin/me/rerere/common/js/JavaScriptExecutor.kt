package me.rerere.common.js

import kotlinx.serialization.json.Json

data class JavaScriptExecutionRequest(
    val code: String,
    val setupScripts: List<String> = emptyList(),
    val timeoutMillis: Long = 30_000,
)

data class JavaScriptExecution(
    val value: JavaScriptValue,
    val console: List<JavaScriptConsoleMessage>,
)

sealed interface JavaScriptValue {
    data object Null : JavaScriptValue

    data class Scalar(val value: String) : JavaScriptValue

    data class Json(val value: String) : JavaScriptValue
}

fun JavaScriptValue.textOrNull(): String? = when (this) {
    JavaScriptValue.Null -> null
    is JavaScriptValue.Scalar -> value
    is JavaScriptValue.Json -> value
}

enum class JavaScriptConsoleLevel {
    LOG,
    INFO,
    WARN,
    ERROR,
}

data class JavaScriptConsoleMessage(
    val level: JavaScriptConsoleLevel,
    val message: String?,
)

interface JavaScriptExecutor {
    suspend fun execute(request: JavaScriptExecutionRequest): JavaScriptExecution
}

class JavaScriptTimeoutException(
    val timeoutMillis: Long,
) : RuntimeException("JavaScript execution timed out after $timeoutMillis ms")

internal val javaScriptJson = Json { ignoreUnknownKeys = true }
