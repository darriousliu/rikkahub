package me.rerere.rikkahub.data.ai

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.JsObject
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.highlight.JsObjectSerializer


class LocalTools() {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    val javascriptTool by lazy {
        Tool(
            name = "eval_javascript",
            description = "Execute JavaScript code with QuickJS. If use this tool to calculate math, better to add `toFixed` to the code.",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("code", buildJsonObject {
                            put("type", "string")
                            put("description", "The JavaScript code to execute")
                        })
                    },
                )
            },
            execute = {
                val context = QuickJs.create(Dispatchers.Default)
                val code = it.jsonObject["code"]?.jsonPrimitive?.contentOrNull
                val result = context.evaluate<Any>(code.orEmpty())
                buildJsonObject {
                    put(
                        "result", when (result) {
                            is JsObject -> JsonPrimitive(json.encodeToString(JsObjectSerializer, result))
                            else -> JsonPrimitive(result.toString())
                        }
                    )
                }
            }
        )
    }

    fun getTools(options: List<LocalToolOption>): List<Tool> {
        val tools = mutableListOf<Tool>()
        if (options.contains(LocalToolOption.JavascriptEngine)) {
            tools.add(javascriptTool)
        }
        return tools
    }
}
