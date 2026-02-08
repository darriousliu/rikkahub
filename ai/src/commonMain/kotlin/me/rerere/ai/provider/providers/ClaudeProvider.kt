package me.rerere.ai.provider.providers

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.sse
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonArrayBuilder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.core.TokenUsage
import me.rerere.ai.provider.ImageGenerationParams
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.Provider
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.ImageGenerationResult
import me.rerere.ai.ui.MessageChunk
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageChoice
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.util.RequestBuilder
import me.rerere.ai.util.configureClientWithProxy
import me.rerere.ai.util.configureReferHeaders
import me.rerere.ai.util.encodeBase64
import me.rerere.ai.util.json
import me.rerere.ai.util.mergeCustomBody
import me.rerere.ai.util.parseErrorDetail
import me.rerere.ai.util.stringSafe
import me.rerere.ai.util.toHeaders
import me.rerere.common.http.jsonPrimitiveOrNull
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
                error("Failed to get models: ${response.status.value} ${response.stringSafe()}")
            }

            val bodyStr = response.stringSafe().orEmpty()
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
            throw Exception("Failed to get response: ${response.status.value} ${response.stringSafe()}")
        }

        val bodyStr = response.stringSafe().orEmpty()
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

    internal fun buildMessages(messages: List<UIMessage>) = buildJsonArray {
        messages
            .filter { it.isValidToUpload() && it.role != MessageRole.SYSTEM }
            .forEach { message ->
                if (message.role == MessageRole.ASSISTANT) {
                    addAssistantMessage(message)
                } else {
                    addUserMessage(message)
                }
            }
    }

    private fun JsonArrayBuilder.addAssistantMessage(message: UIMessage) {
        val groups = groupPartsByToolBoundary(message.parts)
        val contentBuffer = mutableListOf<JsonObject>()

        for (group in groups) {
            when (group) {
                is PartGroup.Content -> {
                    group.parts.mapNotNull { it.toContentBlock() }.forEach { contentBuffer.add(it) }
                }

                is PartGroup.Tools -> {
                    // 添加 tool_use 到内容缓冲
                    group.tools.forEach { contentBuffer.add(it.toToolUseBlock()) }

                    // 输出 assistant 消息
                    add(buildJsonObject {
                        put("role", "assistant")
                        putJsonArray("content") { contentBuffer.forEach { add(it) } }
                    })
                    contentBuffer.clear()

                    // 紧跟 tool_result
                    add(buildJsonObject {
                        put("role", "user")
                        putJsonArray("content") {
                            group.tools.forEach { add(it.toToolResultBlock()) }
                        }
                    })
                }
            }
        }

        // 输出剩余内容
        if (contentBuffer.isNotEmpty()) {
            add(buildJsonObject {
                put("role", "assistant")
                putJsonArray("content") { contentBuffer.forEach { add(it) } }
            })
        }
    }

    private fun JsonArrayBuilder.addUserMessage(message: UIMessage) {
        add(buildJsonObject {
            put("role", message.role.name.lowercase())
            putJsonArray("content") {
                message.parts.mapNotNull { it.toContentBlock() }.forEach { add(it) }
            }
        })
    }

    private fun UIMessagePart.toContentBlock(): JsonObject? = when (this) {
        is UIMessagePart.Text -> buildJsonObject {
            put("type", "text")
            put("text", text)
        }

        is UIMessagePart.Image -> buildJsonObject {
            encodeBase64(withPrefix = false).onSuccess { encoded ->
                put("type", "image")
                put("source", buildJsonObject {
                    put("type", "base64")
                    put("media_type", encoded.mimeType)
                    put("data", encoded.base64)
                })
            }.onFailure {
                Logger.w(TAG, it) { "encode image failed: $url" }
                put("type", "text")
                put("text", "")
            }
        }

        is UIMessagePart.Reasoning -> buildJsonObject {
            put("type", "thinking")
            put("thinking", reasoning)
            metadata?.forEach { (key, value) -> put(key, value) }
        }

        else -> null
    }

    private fun UIMessagePart.Tool.toToolUseBlock() = buildJsonObject {
        put("type", "tool_use")
        put("id", toolCallId)
        put("name", toolName)
        put("input", json.parseToJsonElement(input.ifBlank { "{}" }))
    }

    private fun UIMessagePart.Tool.toToolResultBlock() = buildJsonObject {
        put("type", "tool_result")
        put("tool_use_id", toolCallId)
        put("content", output.filterIsInstance<UIMessagePart.Text>().joinToString("\n") { it.text })
    }

    private fun parseMessage(content: JsonArray): UIMessage {
        val parts = mutableListOf<UIMessagePart>()

        content.forEach { contentBlock ->
            val block = contentBlock.jsonObject
            val type = block["type"]?.jsonPrimitive?.contentOrNull

            when (type) {
                "text", "text_delta" -> {
                    val text = block["text"]?.jsonPrimitive?.contentOrNull ?: ""
                    if (text.isNotEmpty()) {
                        parts.add(UIMessagePart.Text(text))
                    }
                }

                "thinking", "thinking_delta", "signature_delta" -> {
                    val thinking = block["thinking"]?.jsonPrimitive?.contentOrNull ?: ""
                    val signature = block["signature"]?.jsonPrimitive?.contentOrNull
                    if (thinking.isNotEmpty() || signature != null) {
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
                }

                "redacted_thinking" -> {
                    error("redacted_thinking detected, not support yet!")
                }

                "tool_use" -> {
                    val id = block["id"]?.jsonPrimitive?.contentOrNull ?: ""
                    val name = block["name"]?.jsonPrimitive?.contentOrNull ?: ""
                    val input = block["input"]?.jsonObject ?: JsonObject(emptyMap())
                    parts.add(
                        UIMessagePart.Tool(
                            toolCallId = id,
                            toolName = name,
                            input = if (input.isEmpty()) "" else json.encodeToString(input),
                            output = emptyList()
                        )
                    )
                }

                "input_json_delta" -> {
                    val input = block["partial_json"]?.jsonPrimitive?.contentOrNull
                    parts.add(
                        UIMessagePart.Tool(
                            toolCallId = "",
                            toolName = "",
                            input = input ?: "",
                            output = emptyList()
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
                (jsonObject["output_tokens"]?.jsonPrimitive?.intOrNull ?: 0),
            cachedTokens = jsonObject["cache_read_input_tokens"]?.jsonPrimitiveOrNull?.intOrNull ?: 0,
        )
    }
}
