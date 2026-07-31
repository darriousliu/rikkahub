package me.rerere.common.text

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

fun decodeJsonEscapedString(value: String): String =
    Json.decodeFromString(value.toJsonStringLiteral())

private fun String.toJsonStringLiteral(): String {
    val value = this
    return buildString(value.length + 2) {
        append('"')
        var index = 0
        while (index < value.length) {
            val character = value[index]
            when {
                character == '\\' && index + 1 < value.length -> {
                    append(character)
                    append(value[index + 1])
                    index += 2
                }

                character == '"' -> {
                    append("\\\"")
                    index++
                }

                character == '\b' -> {
                    append("\\b")
                    index++
                }

                character == '\t' -> {
                    append("\\t")
                    index++
                }

                character == '\n' -> {
                    append("\\n")
                    index++
                }

                character == '\u000C' -> {
                    append("\\f")
                    index++
                }

                character == '\r' -> {
                    append("\\r")
                    index++
                }

                character.code < 0x20 -> {
                    append("\\u")
                    append(character.code.toString(16).padStart(4, '0'))
                    index++
                }

                else -> {
                    append(character)
                    index++
                }
            }
        }
        append('"')
    }
}
