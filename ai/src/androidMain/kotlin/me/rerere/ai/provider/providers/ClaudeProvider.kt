package me.rerere.ai.provider.providers

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.sse
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.core.TokenUsage
import me.rerere.ai.provider.*
import me.rerere.ai.ui.*
import me.rerere.ai.util.*
import kotlin.time.Clock

private const val TAG = "ClaudeProvider"
private const val ANTHROPIC_VERSION = "2023-06-01"

class ClaudeProvider(private val client: HttpClient) : Provider<ProviderSetting.Claude> {
    override suspend fun listModels(providerSetting: ProviderSetting.Claude): List<Model> =
        withContext(Dispatchers.IO) {
            val request: RequestBuilder = {
                url("${providerSetting.baseUrl}/models")
                headers {
                    append("x-api-key", providerSetting.apiKey)
                    append("anthropic-version", ANTHROPIC_VERSION)
                }
            }

            val response = client.configureClientWithProxy(providerSetting.proxy).get(request)

            if (!response.status.isSuccess()) {
                error("Failed to get models: ${response.status.value} ${response.bodyAsText()}")
            }

            val bodyStr = response.bodyAsText()
            val bodyJson = json.parseToJsonElement(bodyStr).jsonObject
            val data = bodyJson["data"]?.jsonArray ?: return@withContext emptyList()

            data.mapNotNull { modelJson ->
                val modelObj = modelJson.jsonObject
                val id = modelObj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val displayName = modelObj["display_name"]?.jsonPrimitive?.contentOrNull ?: id

                Model(
                    modelId = id,
                    displayName = displayName,
                )
            }
        }

    override suspend fun generateImage(
        providerSetting: ProviderSetting,
        params: ImageGenerationParams
    ): ImageGenerationResult {
        error("Claude provider does not support image generation")
    }

    override suspend fun generateText(
        providerSetting: ProviderSetting.Claude,
        messages: List<UIMessage>,
        params: TextGenerationParams
    ): MessageChunk = withContext(Dispatchers.IO) {
        val requestBody = buildMessageRequest(messages, params)
        val request: RequestBuilder = {
            url("${providerSetting.baseUrl}/messages")
            headers {
                appendAll(params.customHeaders.toHeaders())
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(requestBody))
                append("x-api-key", providerSetting.apiKey)
                append("anthropic-version", ANTHROPIC_VERSION)
                configureReferHeaders(providerSetting.baseUrl)
            }
        }
        Logger.i(TAG) { "generateText: ${json.encodeToString(requestBody)}" }

        val response = client.configureClientWithProxy(providerSetting.proxy).post(request)

        if (!response.status.isSuccess()) {
            throw Exception("Failed to get response: ${response.status.value} ${response.bodyAsText()}")
        }

        val bodyStr = response.bodyAsText()
        val bodyJson = json.parseToJsonElement(bodyStr).jsonObject

        // 从 JsonObject 中提取必要的信息
        val id = bodyJson["id"]?.jsonPrimitive?.contentOrNull ?: ""
        val model = bodyJson["model"]?.jsonPrimitive?.contentOrNull ?: ""
        val content = bodyJson["content"]?.jsonArray ?: JsonArray(emptyList())
        val stopReason = bodyJson["stop_reason"]?.jsonPrimitive?.contentOrNull ?: "unknown"
        val usage = parseTokenUsage(bodyJson["usage"] as? JsonObject)

        MessageChunk(
            id = id,
            model = model,
            choices = listOf(
                UIMessageChoice(
                    index = 0,
                    delta = null,
                    message = parseMessage(content),
                    finishReason = stopReason
                )
            ),
            usage = usage
        )
    }

    override suspend fun streamText(
        providerSetting: ProviderSetting.Claude,
        messages: List<UIMessage>,
        params: TextGenerationParams
    ): Flow<MessageChunk> = flow {
        val requestBody = buildMessageRequest(messages, params, stream = true)
        val request: RequestBuilder = {
            url("${providerSetting.baseUrl}/messages")
            headers {
                appendAll(params.customHeaders.toHeaders())
                method = HttpMethod.Post
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(requestBody))
                append("x-api-key", providerSetting.apiKey)
                append("anthropic-version", ANTHROPIC_VERSION)
                append("Content-Type", "application/json")
                configureReferHeaders(providerSetting.baseUrl)
            }
        }

        Logger.i(TAG) { "streamText: ${json.encodeToString(requestBody)}" }

        requestBody["messages"]!!.jsonArray.forEach {
            Logger.i(TAG) { "streamText: $it" }
        }

        client.configureClientWithProxy(providerSetting.proxy).sse(request) {
            try {
                incoming.collect { event ->
                    Logger.d(TAG) { "onEvent: type=${event.event}, data=${event.data}" }

                    when (event.event) {
                        "message_stop" -> {
                            Logger.d(TAG) { "Stream ended" }
                        }

                        "error" -> {
                            val eventData = json.parseToJsonElement(event.data.orEmpty()).jsonObject
                            val error = eventData["error"]?.parseErrorDetail()
                            if (error != null) {
                                throw error
                            }
                        }

                        else -> {
                            // 处理普通数据事件
                            val data = event.data.orEmpty()
                            val dataJson = json.parseToJsonElement(data).jsonObject
                            val deltaMessage = parseMessage(buildJsonArray {
                                val contentBlockObj = dataJson["content_block"]?.jsonObject
                                val deltaObj = dataJson["delta"]?.jsonObject
                                if (contentBlockObj != null) {
                                    add(contentBlockObj)
                                }
                                if (deltaObj != null) {
                                    add(deltaObj)
                                }
                            })
                            val tokenUsage = parseTokenUsage(
                                dataJson["usage"]?.jsonObject
                                    ?: dataJson["message"]?.jsonObject?.get("usage")?.jsonObject
                            )
                            val messageChunk = MessageChunk(
                                id = event.id.orEmpty(),
                                model = "",
                                choices = listOf(
                                    UIMessageChoice(
                                        index = 0,
                                        delta = deltaMessage,
                                        message = null,
                                        finishReason = null
                                    )
                                ),
                                usage = tokenUsage
                            )
                            emit(messageChunk)
                        }
                    }
                }
            } catch (t: Throwable) {
                val response = call.response
                var exception = t

                t.printStackTrace()
                Logger.e(TAG) { "onFailure: ${t::class.qualifiedName} ${t.message} / $response" }

                val bodyRaw = response.stringSafe()
                runCatching {
                    if (!bodyRaw.isNullOrEmpty()) {
                        val bodyElement = Json.parseToJsonElement(bodyRaw)
                        Logger.i(TAG) { "Error response: $bodyElement" }
                        exception = bodyElement.parseErrorDetail()
                    }
                    exception.printStackTrace()
                }.onFailure { e ->
                    Logger.w(TAG) { "onFailure: failed to parse from $bodyRaw" }
                    e.printStackTrace()
                }
            } finally {
                Logger.d(TAG) { "Closing eventSource" }
            }
        }
    }

    private fun buildMessageRequest(
        messages: List<UIMessage>,
        params: TextGenerationParams,
        stream: Boolean = false
    ): JsonObject {
        return buildJsonObject {
            put("model", params.model.modelId)
            put("messages", buildMessages(messages))
            put("max_tokens", params.maxTokens ?: 64_000)

            if (params.temperature != null && (params.thinkingBudget ?: 0) == 0) put(
                "temperature",
                params.temperature
            )
            if (params.topP != null) put("top_p", params.topP)

            put("stream", stream)

            // system prompt
            val systemMessage = messages.firstOrNull { it.role == MessageRole.SYSTEM }
            if (systemMessage != null) {
                put("system", buildJsonArray {
                    systemMessage.parts.filterIsInstance<UIMessagePart.Text>().forEach { part ->
                        add(buildJsonObject {
                            put("type", "text")
                            put("text", part.text)
                        })
                    }
                })
            }

            // 处理 thinking budget
            if (params.model.abilities.contains(ModelAbility.REASONING)) {
                val level = ReasoningLevel.fromBudgetTokens(params.thinkingBudget ?: 0)
                put("thinking", buildJsonObject {
                    if (level == ReasoningLevel.OFF) {
                        put("type", "disabled")
                    } else {
                        put("type", "enabled")
                        if (level != ReasoningLevel.AUTO) put("budget_tokens", params.thinkingBudget ?: 0)
                    }
                })
            }

            // 处理工具
            if (params.model.abilities.contains(ModelAbility.TOOL) && params.tools.isNotEmpty()) {
                putJsonArray("tools") {
                    params.tools.forEach { tool ->
                        add(buildJsonObject {
                            put("name", tool.name)
                            put("description", tool.description)
                            put("input_schema", json.encodeToJsonElement(tool.parameters()))
                        })
                    }
                }
            }
        }.mergeCustomBody(params.customBody)
    }

    private fun buildMessages(messages: List<UIMessage>) = buildJsonArray {
        messages
            .filter { it.isValidToUpload() && it.role != MessageRole.SYSTEM }
            .forEach { message ->
                if (message.role == MessageRole.TOOL) {
                    message.getToolResults().forEach { result ->
                        add(buildJsonObject {
                            put("role", "user")
                            putJsonArray("content") {
                                add(buildJsonObject {
                                    put("type", "tool_result")
                                    put("tool_use_id", result.toolCallId)
                                    put("content", json.encodeToString(result.content))
                                })
                            }
                        })
                    }
                    return@forEach
                }

                add(buildJsonObject {
                    // role
                    put("role", JsonPrimitive(message.role.name.lowercase()))

                    // content
                    putJsonArray("content") {
                        message.parts.forEach { part ->
                            when (part) {
                                is UIMessagePart.Text -> {
                                    add(buildJsonObject {
                                        put("type", "text")
                                        put("text", part.text)
                                    })
                                }

                                is UIMessagePart.Image -> {
                                    add(buildJsonObject {
                                        part.encodeBase64().onSuccess { base64Data ->
                                            put("type", "image")
                                            put("source", buildJsonObject {
                                                put("type", "base64")
                                                put(
                                                    "media_type",
                                                    "image/jpeg"
                                                ) // 默认为 jpeg，可能需要根据实际情况调整
                                                put(
                                                    "data",
                                                    base64Data.substringAfter(",")
                                                ) // 移除 data:image/jpeg;base64, 前缀
                                            })
                                        }.onFailure {
                                            it.printStackTrace()
                                            Logger.w(TAG) { "encode image failed: ${part.url}" }
                                            // 如果图片编码失败，添加一个空文本块
                                            put("type", "text")
                                            put("text", "")
                                        }
                                    })
                                }

                                is UIMessagePart.ToolCall -> {
                                    add(buildJsonObject {
                                        put("type", "tool_use")
                                        put("id", part.toolCallId)
                                        put("name", part.toolName)
                                        put("input", json.parseToJsonElement(part.arguments))
                                    })
                                }

                                is UIMessagePart.Reasoning -> {
                                    add(buildJsonObject {
                                        put("type", "thinking")
                                        put("thinking", part.reasoning)
                                        part.metadata?.let {
                                            it.forEach { entry ->
                                                put(entry.key, entry.value)
                                            }
                                        }
                                    })
                                }

                                else -> {
                                    Logger.w(TAG) { "buildMessages: message part not supported: $part" }
                                    // DO NOTHING
                                }
                            }
                        }
                    }
                })
            }
    }

    private fun parseMessage(content: JsonArray): UIMessage {
        val parts = mutableListOf<UIMessagePart>()

        content.forEach { contentBlock ->
            val block = contentBlock.jsonObject
            val type = block["type"]?.jsonPrimitive?.contentOrNull

            when (type) {
                "text", "text_delta" -> {
                    val text = block["text"]?.jsonPrimitive?.contentOrNull ?: ""
                    parts.add(UIMessagePart.Text(text))
                }

                "thinking", "thinking_delta", "signature_delta" -> {
                    val thinking = block["thinking"]?.jsonPrimitive?.contentOrNull ?: ""
                    val signature = block["signature"]?.jsonPrimitive?.contentOrNull
                    val reasoning = UIMessagePart.Reasoning(
                        reasoning = thinking,
                        createdAt = Clock.System.now(),
                    )
                    if (signature != null) {
                        reasoning.metadata = buildJsonObject {
                            put("signature", signature)
                        }
                    }
                    parts.add(reasoning)
                }

                "redacted_thinking" -> {
                    error("redacted_thinking detected, not support yet!")
                }

                "tool_use" -> {
                    val id = block["id"]?.jsonPrimitive?.contentOrNull ?: ""
                    val name = block["name"]?.jsonPrimitive?.contentOrNull ?: ""
                    val input = block["input"]?.jsonObject ?: JsonObject(emptyMap())
                    parts.add(
                        UIMessagePart.ToolCall(
                            toolCallId = id,
                            toolName = name,
                            arguments = if (input.isEmpty()) "" else json.encodeToString(input)
                        )
                    )
                }

                "input_json_delta" -> {
                    val input = block["partial_json"]?.jsonPrimitive?.contentOrNull
                    parts.add(
                        UIMessagePart.ToolCall(
                            toolCallId = "",
                            toolName = "",
                            arguments = input ?: ""
                        )
                    )
                }
            }
        }

        return UIMessage(
            role = MessageRole.ASSISTANT,
            parts = parts
        )
    }

    private fun parseTokenUsage(jsonObject: JsonObject?): TokenUsage? {
        if (jsonObject == null) return null
        return TokenUsage(
            promptTokens = jsonObject["input_tokens"]?.jsonPrimitive?.intOrNull ?: 0,
            completionTokens = jsonObject["output_tokens"]?.jsonPrimitive?.intOrNull ?: 0,
            totalTokens = (jsonObject["input_tokens"]?.jsonPrimitive?.intOrNull ?: 0) +
                (jsonObject["output_tokens"]?.jsonPrimitive?.intOrNull ?: 0)
        )
    }
}
