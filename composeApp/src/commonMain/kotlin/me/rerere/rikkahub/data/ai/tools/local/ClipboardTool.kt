package me.rerere.rikkahub.data.ai.tools.local

import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.platform.PlatformClipboard

internal fun buildClipboardTool(): Tool = Tool(
    name = "clipboard_tool",
    description = """
        Read or write plain text from the device clipboard.
        Use action: read or write. For write, provide text.
        Do NOT write to the clipboard unless the user has explicitly requested it.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("action", buildJsonObject {
                    put("type", "string")
                    put(
                        "enum",
                        buildJsonArray {
                            add("read")
                            add("write")
                        }
                    )
                    put("description", "Operation to perform: read or write")
                })
                put("text", buildJsonObject {
                    put("type", "string")
                    put("description", "Text to write to the clipboard (required for write)")
                })
            },
            required = listOf("action")
        )
    },
    execute = {
        val params = it.jsonObject
        val action = params["action"]?.jsonPrimitive?.contentOrNull ?: error("action is required")
        when (action) {
            "read" -> {
                val payload = buildJsonObject {
                    put("text", PlatformClipboard.readText())
                }
                listOf(UIMessagePart.Text(payload.toString()))
            }

            "write" -> {
                val text = params["text"]?.jsonPrimitive?.contentOrNull ?: error("text is required")
                PlatformClipboard.writeText(text)
                val payload = buildJsonObject {
                    put("success", true)
                    put("text", text)
                }
                listOf(UIMessagePart.Text(payload.toString()))
            }

            else -> error("unknown action: $action, must be one of [read, write]")
        }
    }
)
