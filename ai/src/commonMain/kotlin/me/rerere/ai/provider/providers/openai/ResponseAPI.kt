package me.rerere.ai.provider.providers.openai

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.sse
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.*
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.core.TokenUsage
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.registry.ModelRegistry
import me.rerere.ai.ui.MessageChunk
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageChoice
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.util.*
import kotlin.time.Clock

private const val TAG = "ResponseAPI"

class ResponseAPI(private val client: HttpClient) : OpenAIImpl {
    override suspend fun generateText(
        providerSetting: ProviderSetting.OpenAI,
        messages: List<UIMessage>,
        params: TextGenerationParams
    ): MessageChunk {
        val requestBody = buildRequestBody(
            messages = messages,
            params = params,
            stream = false,
        )
        val request = HttpRequestBuilder().apply {
            url("${providerSetting.baseUrl}/responses")
            headers.appendAll(params.customHeaders.toHeaders())
            contentType(ContentType.Application.Json)
            setBody(requestBody)
            header("Authorization", "Bearer ${providerSetting.apiKey}")
            configureReferHeaders(providerSetting.baseUrl)
        }

        Logger.i(TAG) { "generateText: ${json.encodeToString(requestBody)}" }

        val response = client.configureClientWithProxy(providerSetting.proxy).post(request)
        if (!response.status.isSuccess()) {
            throw Exception("Failed to get response: ${response.status.value} ${response.bodyAsText()}")
        }

        val bodyStr = response.stringSafe().orEmpty()
        Logger.i(TAG) { "generateText: $bodyStr" }
        val bodyJson = json.parseToJsonElement(bodyStr).jsonObject
        val output = parseResponseOutput(bodyJson)

        return output
    }

    override suspend fun streamText(
        providerSetting: ProviderSetting.OpenAI,
        messages: List<UIMessage>,
        params: TextGenerationParams
    ): Flow<MessageChunk> = flow {
        val requestBody = buildRequestBody(
            messages = messages,
            params = params,
            stream = true,
        )
        val request = HttpRequestBuilder().apply {
            url("${providerSetting.baseUrl}/responses")
            headers.appendAll(params.customHeaders.toHeaders())
            method = HttpMethod.Post
            contentType(ContentType.Application.Json)
            setBody(requestBody)
            header("Authorization", "Bearer ${providerSetting.apiKey}")
            configureReferHeaders(providerSetting.baseUrl)
        }

        Logger.i(TAG) { "streamText: ${json.encodeToString(requestBody)}" }

        client.configureClientWithProxy(providerSetting.proxy).sse({ takeFrom(request) }) {
            try {
                incoming.collect { event ->
                    val id = event.id
                    val type = event.event
                    val data = event.data ?: return@collect
                    Logger.i(TAG) { "onEvent: $id/$type $data" }
                    val json = json.parseToJsonElement(data).jsonObject
                    val chunk = parseResponseDelta(json)
                    if (chunk != null) {
                        emit(chunk)
                    }
                    if (type == "response.completed") {
                        cancel()
                    }
                }
            } catch (t: Throwable) {
                val response = call.response
                var exception: Throwable

                t.printStackTrace()
                println("[onFailure] 发生错误: ${t::class.qualifiedName} ${t.message} / $response")

                val bodyRaw = response.stringSafe()
                try {
                    if (!bodyRaw.isNullOrEmpty()) {
                        val bodyElement = Json.parseToJsonElement(bodyRaw)
                        println(bodyElement)
                        exception = bodyElement.parseErrorDetail()
                        Logger.i(TAG) { "onFailure: $exception" }
                    }
                } catch (e: Throwable) {
                    Logger.i(TAG) { "onFailure: failed to parse from $bodyRaw" }
                    e.printStackTrace()
                }
            } finally {
                println("[awaitClose] 关闭eventSource ")
            }
        }
    }

    private fun buildRequestBody(
        messages: List<UIMessage>,
        params: TextGenerationParams,
        stream: Boolean
    ): JsonObject {
        return buildJsonObject {
            put("model", params.model.modelId)
            put("stream", stream)

            if (isModelAllowTemperature(params.model)) {
                if (params.temperature != null) put("temperature", params.temperature)
                if (params.topP != null) put("top_p", params.topP)
            }
            if (params.maxTokens != null) put("max_output_tokens", params.maxTokens)

            // system instructions
            if (messages.any { it.role == MessageRole.SYSTEM }) {
                val parts = messages.first { it.role == MessageRole.SYSTEM }.parts
                put(
                    "instructions",
                    parts.filterIsInstance<UIMessagePart.Text>().joinToString("\n") { it.text })
            }

            // messages
            put("input", buildMessages(messages))

            // reasoning
            if (params.model.abilities.contains(ModelAbility.REASONING)) {
                val level = ReasoningLevel.fromBudgetTokens(params.thinkingBudget ?: 0)
                put("reasoning", buildJsonObject {
                    put("summary", "auto")
                    if (level != ReasoningLevel.AUTO) {
                        put("effort", level.effort)
                    }
                })
            }

            // tools
            if (params.model.abilities.contains(ModelAbility.TOOL) && params.tools.isNotEmpty()) {
                putJsonArray("tools") {
                    params.tools.forEach { tool ->
                        add(buildJsonObject {
                            put("type", "function")
                            put("name", tool.name)
                            put("description", tool.description)
                            put(
                                "parameters",
                                json.encodeToJsonElement(
                                    tool.parameters()
                                )
                            )
                        })
                    }
                }
            }
        }.mergeCustomBody(params.customBody)
    }

    private fun buildMessages(messages: List<UIMessage>) = buildJsonArray {
        messages
            .filter {
                it.isValidToUpload() && it.role != MessageRole.SYSTEM
            }
            .forEachIndexed { index, message ->
                if (message.role == MessageRole.TOOL) {
                    message.getToolResults().forEach { result ->
                        add(buildJsonObject {
                            put("type", "function_call_output")
                            put("call_id", result.toolCallId)
                            put("output", json.encodeToString(result.content))
                        })
                    }
                    return@forEachIndexed
                }
                add(buildJsonObject {
                    // role
                    put("role", JsonPrimitive(message.role.name.lowercase()))

                    // content
                    if (message.parts.isOnlyTextPart()) {
                        // 如果只是纯文本，直接赋值给content
                        put(
                            "content",
                            message.parts.filterIsInstance<UIMessagePart.Text>().first().text
                        )
                    } else {
                        // 否则，使用parts构建
                        putJsonArray("content") {
                            message.parts.forEach { part ->
                                when (part) {
                                    is UIMessagePart.Text -> {
                                        add(buildJsonObject {
                                            put(
                                                "type",
                                                if (message.role == MessageRole.USER) "input_text" else "output_text"
                                            )
                                            put("text", part.text)
                                        })
                                    }

                                    is UIMessagePart.Image -> {
                                        add(buildJsonObject {
                                            part.encodeBase64().onSuccess {
                                                put(
                                                    "type",
                                                    if (message.role == MessageRole.USER) "input_image" else "output_image"
                                                )
                                                put("image_url", it)
                                            }.onFailure {
                                                it.printStackTrace()
                                                println("encode image failed: ${part.url}")

                                                put("type", "input_text")
                                                put(
                                                    "text",
                                                    "Error: Failed to encode image to base64"
                                                )
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
                    }
                })
                // tool_calls
                message.getToolCalls()
                    .takeIf { it.isNotEmpty() }
                    ?.let { toolCalls ->
                        toolCalls.forEach { toolCall ->
                            add(buildJsonObject {
                                put("type", "function_call")
                                put("call_id", toolCall.toolCallId)
                                put("name", toolCall.toolName)
                                put("arguments", toolCall.arguments)
                            })
                        }
                    }
            }
    }

    private fun parseResponseDelta(jsonObject: JsonObject): MessageChunk? {
        val chunkType = jsonObject["type"]?.jsonPrimitive?.content ?: error("chunk type not found")

        when (chunkType) {
            "response.output_text.delta" -> {
                return MessageChunk(
                    id = jsonObject["item_id"]?.jsonPrimitive?.contentOrNull ?: "",
                    model = "",
                    choices = listOf(
                        UIMessageChoice(
                            index = 0,
                            delta = UIMessage.assistant(
                                jsonObject["delta"]?.jsonPrimitive?.contentOrNull ?: ""
                            ),
                            message = null,
                            finishReason = null
                        )
                    )
                )
            }

            "response.reasoning_summary_text.delta" -> {
                return MessageChunk(
                    id = jsonObject["item_id"]?.jsonPrimitive?.contentOrNull ?: "",
                    model = "",
                    choices = listOf(
                        UIMessageChoice(
                            index = 0,
                            delta = UIMessage(
                                role = MessageRole.ASSISTANT,
                                parts = listOf(
                                    UIMessagePart.Reasoning(
                                        reasoning = jsonObject["delta"]?.jsonPrimitive?.contentOrNull
                                            ?: "",
                                        createdAt = Clock.System.now(),
                                        finishedAt = null
                                    )
                                )
                            ),
                            message = null,
                            finishReason = null
                        )
                    )
                )
            }

            "response.output_item.added" -> {
                val item = jsonObject["item"]?.jsonObject ?: error("chunk item not found")
                val type = item["type"]?.jsonPrimitive?.content ?: error("chunk type not found")
                val id = item["id"]?.jsonPrimitive?.content ?: error("chunk id not found")
                if (type == "function_call") {
                    return MessageChunk(
                        id = id,
                        model = "",
                        choices = listOf(
                            UIMessageChoice(
                                index = 0,
                                message = null,
                                delta = UIMessage(
                                    role = MessageRole.ASSISTANT,
                                    parts = listOf(
                                        UIMessagePart.ToolCall(
                                            toolCallId = id,
                                            toolName = item["name"]?.jsonPrimitive?.content ?: "",
                                            arguments = item["arguments"]?.jsonPrimitive?.content
                                                ?: ""
                                        )
                                    )
                                ),
                                finishReason = null
                            )
                        )
                    )
                } else if (type == "reasoning") {
                    return MessageChunk(
                        id = id,
                        model = "",
                        choices = listOf(
                            UIMessageChoice(
                                index = 0,
                                message = null,
                                delta = UIMessage(
                                    role = MessageRole.ASSISTANT,
                                    parts = listOf(
                                        UIMessagePart.Reasoning(
                                            reasoning = "",
                                            createdAt = Clock.System.now(),
                                            finishedAt = null,
                                        )
                                    )
                                ),
                                finishReason = null,
                            )
                        )
                    )
                }
            }

            "response.function_call_arguments.done" -> {
                val toolCallId =
                    jsonObject["item_id"]?.jsonPrimitive?.content ?: error("item_id not found")
                val arguments =
                    jsonObject["arguments"]?.jsonPrimitive?.content ?: error("arguments not found")
                return MessageChunk(
                    id = toolCallId,
                    model = "",
                    choices = listOf(
                        UIMessageChoice(
                            index = 0,
                            delta = UIMessage(
                                role = MessageRole.ASSISTANT,
                                parts = listOf(
                                    UIMessagePart.ToolCall(
                                        toolCallId = toolCallId,
                                        toolName = "",
                                        arguments = arguments,
                                    )
                                )
                            ),
                            message = null,
                            finishReason = null
                        )
                    ),
                )
            }

            "response.completed" -> {
                return MessageChunk(
                    id = jsonObject["item_id"]?.jsonPrimitive?.contentOrNull ?: "",
                    model = "",
                    choices = emptyList(),
                    usage = parseTokenUsage(jsonObject["response"]?.jsonObject?.get("usage")?.jsonObject)
                )
            }
        }

        return null
    }

    private fun parseResponseOutput(jsonObject: JsonObject): MessageChunk {
        println(jsonObject)
        val outputs = jsonObject["output"]?.jsonArray ?: error("output not found")
        val parts = arrayListOf<UIMessagePart>()

        outputs.forEach { outputItem ->
            val output = outputItem.jsonObject
            val type = output["type"]?.jsonPrimitive?.content ?: error("output type not found")
            when (type) {
                "reasoning" -> {
                    val summary = output["summary"]?.jsonArray ?: error("summary not found")
                    summary.map { it.jsonObject }.forEach { part ->
                        val partType = part["type"]?.jsonPrimitive?.content ?: error("part type not found")
                        when (partType) {
                            "summary_text" -> {
                                val text = part["text"]?.jsonPrimitive?.content ?: error("text not found")
                                parts.add(
                                    UIMessagePart.Reasoning(
                                        reasoning = text,
                                        createdAt = Clock.System.now(),
                                        finishedAt = Clock.System.now()
                                    )
                                )
                            }
                        }
                    }
                }

                "function_call" -> {
                    val callId = output["call_id"]?.jsonPrimitive?.content ?: error("call_id not found")
                    val name = output["name"]?.jsonPrimitive?.content ?: error("name not found")
                    val arguments =
                        output["arguments"]?.jsonPrimitive?.content ?: error("arguments not found")
                    parts.add(
                        UIMessagePart.ToolCall(
                            toolCallId = callId,
                            toolName = name,
                            arguments = arguments
                        )
                    )
                }

                "message" -> {
                    val content = output["content"]?.jsonArray ?: error("content not found")
                    content.map { it.jsonObject }.forEach { part ->
                        val partType = part["type"]?.jsonPrimitive?.content ?: error("part type not found")
                        when (partType) {
                            "output_text" -> {
                                val text = part["text"]?.jsonPrimitive?.content ?: error("text not found")
                                parts.add(
                                    UIMessagePart.Text(
                                        text = text
                                    )
                                )
                            }

                            else -> error("unknown part type $partType")
                        }
                    }
                }
            }
        }

        return MessageChunk(
            id = jsonObject["id"]?.jsonPrimitive?.contentOrNull ?: "",
            model = jsonObject["model"]?.jsonPrimitive?.contentOrNull ?: "",
            choices = listOf(
                UIMessageChoice(
                    index = 0,
                    message = UIMessage(
                        role = MessageRole.ASSISTANT,
                        parts = parts,
                    ),
                    finishReason = null,
                    delta = null
                )
            ),
            usage = parseTokenUsage(jsonObject["usage"]?.jsonObject)
        )
    }

    private fun parseTokenUsage(jsonObject: JsonObject?): TokenUsage? {
        if (jsonObject == null) return null
        return TokenUsage(
            promptTokens = jsonObject["input_tokens"]?.jsonPrimitive?.intOrNull ?: 0,
            completionTokens = jsonObject["output_tokens"]?.jsonPrimitive?.intOrNull ?: 0,
            totalTokens = jsonObject["total_tokens"]?.jsonPrimitive?.intOrNull ?: 0,
        )
    }
}

private fun isModelAllowTemperature(model: Model): Boolean {
    return !ModelRegistry.OPENAI_O_MODELS.match(model.modelId) && !ModelRegistry.GPT_5.match(model.modelId)
}

private fun List<UIMessagePart>.isOnlyTextPart(): Boolean {
    val gonnaSend = filter { it is UIMessagePart.Text || it is UIMessagePart.Image }.size
    val texts = filter { it is UIMessagePart.Text }.size
    return gonnaSend == texts && texts == 1
}
