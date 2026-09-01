package me.rerere.rikkahub.data.ai.tools.local

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.common.js.DefaultJavaScriptExecutor
import me.rerere.common.js.JavaScriptConsoleLevel
import me.rerere.common.js.JavaScriptExecutionRequest
import me.rerere.common.js.JavaScriptExecutor
import me.rerere.common.js.JavaScriptValue

internal fun buildJavascriptTool(
    javaScriptExecutor: JavaScriptExecutor = DefaultJavaScriptExecutor(),
): Tool = Tool(
    name = "eval_javascript",
    description = """
        Execute JavaScript code using QuickJS engine (ES2020).
        The result is the value of the last expression in the code.
        For calculations with decimals, use toFixed() to control precision.
        Console output (log/info/warn/error) is captured and returned in 'logs' field.
        No DOM or Node.js APIs available.
        Example: '1 + 2' returns 3; 'const x = 5; x * 2' returns 10.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("code", buildJsonObject {
                    put("type", "string")
                    put("description", "The JavaScript code to execute")
                })
            },
            required = listOf("code")
        )
    },
    execute = {
        val code = it.jsonObject["code"]?.jsonPrimitive?.contentOrNull
            ?: error("code is required")
        val execution = javaScriptExecutor.execute(JavaScriptExecutionRequest(code = code))
        val payload = buildJsonObject {
            if (execution.console.isNotEmpty()) {
                put(
                    "logs",
                    JsonPrimitive(
                        execution.console.joinToString("\n") { message ->
                            "[${message.level.toolLabel()}] ${message.message}"
                        },
                    ),
                )
            }
            put(
                key = "result",
                element = when (val value = execution.value) {
                    JavaScriptValue.Null -> JsonNull
                    is JavaScriptValue.Scalar -> JsonPrimitive(value.value)
                    is JavaScriptValue.Json -> JsonPrimitive(value.value)
                }
            )
        }
        listOf(UIMessagePart.Text(payload.toString()))
    }
)

private fun JavaScriptConsoleLevel.toolLabel(): String = when (this) {
    JavaScriptConsoleLevel.LOG -> "LOG"
    JavaScriptConsoleLevel.INFO -> "INFO"
    JavaScriptConsoleLevel.WARN -> "WARN"
    JavaScriptConsoleLevel.ERROR -> "ERROR"
}
