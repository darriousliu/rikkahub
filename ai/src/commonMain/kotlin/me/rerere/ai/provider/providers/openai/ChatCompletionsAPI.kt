package me.rerere.ai.provider.providers.openai

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.sse
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.core.TokenUsage
import me.rerere.ai.provider.*
import me.rerere.ai.registry.ModelRegistry
import me.rerere.ai.ui.*
import me.rerere.ai.util.*
import me.rerere.common.http.jsonObjectOrNull
import kotlin.time.Clock

private const val TAG = "ChatCompletionsAPI"

class ChatCompletionsAPI(
    private val client: HttpClient,
    private val keyRoulette: KeyRoulette
) : OpenAIImpl {
    override suspend fun generateText(
        providerSetting: ProviderSetting.OpenAI,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): MessageChunk = withContext(Dispatchers.IO) {
        val requestBody =
            buildChatCompletionRequest(
                messages = messages,
                params = params,
                providerSetting = providerSetting
            )

        val proxyClient = client.configureClientWithProxy(providerSetting.proxy)

        val request = HttpRequestBuilder().apply {
            url("${providerSetting.baseUrl}${providerSetting.chatCompletionsPath}")
            headers.appendAll(params.customHeaders.toHeaders())
            contentType(ContentType.Application.Json)
            setBody(requestBody)
            header("Authorization", "Bearer ${keyRoulette.next(providerSetting.apiKey)}")
            configureReferHeaders(providerSetting.baseUrl)
        }

        Logger.i(TAG) { "generateText: ${json.encodeToString(requestBody)}" }

        val response = proxyClient.post(request)
        if (!response.status.isSuccess()) {
            throw Exception("Failed to get response: ${response.status.value} ${response.stringSafe()}")
        }

        val bodyStr = response.stringSafe().orEmpty()
        val bodyJson = json.parseToJsonElement(bodyStr).jsonObject

        // 从 JsonObject 中提取必要的信息
        val id = bodyJson["id"]?.jsonPrimitive?.contentOrNull ?: ""
        val model = bodyJson["model"]?.jsonPrimitive?.contentOrNull ?: ""
        val choice = bodyJson["choices"]?.jsonArray?.get(0)?.jsonObject ?: error("choices is null")

        val message = choice["message"]?.jsonObject ?: throw Exception("message is null")
        val finishReason = choice["finish_reason"]
            ?.jsonPrimitive
            ?.content
            ?: "unknown"
        val usage = parseTokenUsage(bodyJson["usage"] as? JsonObject)

        MessageChunk(
            id = id,
            model = model,
            choices = listOf(
                UIMessageChoice(
                    index = 0,
                    delta = null,
                    message = parseMessage(message),
                    finishReason = finishReason
                )
            ),
            usage = usage
        )
    }

    override suspend fun streamText(
        providerSetting: ProviderSetting.OpenAI,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): Flow<MessageChunk> = flow {
        val requestBody = buildChatCompletionRequest(
            messages = messages,
            params = params,
            providerSetting = providerSetting,
            stream = true,
        )

        val proxyClient = client.configureClientWithProxy(providerSetting.proxy)

        val request = HttpRequestBuilder().apply {
            url("${providerSetting.baseUrl}${providerSetting.chatCompletionsPath}")
            headers.appendAll(params.customHeaders.toHeaders())
            method = HttpMethod.Post
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(requestBody))
            header("Authorization", "Bearer ${keyRoulette.next(providerSetting.apiKey)}")
            header("Content-Type", "application/json")
            configureReferHeaders(providerSetting.baseUrl)
        }

        Logger.i(TAG) { "streamText: ${json.encodeToString(requestBody)}" }

        // just for debugging response body
        // println(client.newCall(request).await().stringSafe())

        proxyClient.sse({ takeFrom(request) }) {
            try {
                incoming.collect { event ->
                    val data = event.data ?: return@collect
                    if (data == "[DONE]") {
                        println("[onEvent] (done) 结束流: $data")
                        return@collect
                    }
                    Logger.i(TAG) { "onEvent: $data" }
                    data
                        .trim()
                        .split("\n")
                        .filter { it.isNotBlank() }
                        .map { json.parseToJsonElement(it).jsonObject }
                        .forEach {
                            if (it["error"] != null) {
                                val error = it["error"]!!.parseErrorDetail()
                                throw error
                            }
                            val id = it["id"]?.jsonPrimitive?.contentOrNull ?: ""
                            val model = it["model"]?.jsonPrimitive?.contentOrNull ?: ""

                            val choices = it["choices"]?.jsonArray ?: JsonArray(emptyList())
                            val choiceList = buildList {
                                if (choices.isNotEmpty()) {
                                    val choice = choices[0].jsonObject
                                    val message =
                                        choice["delta"]?.jsonObject ?: choice["message"]?.jsonObject
                                        ?: throw Exception("delta/message is null")
                                    val finishReason =
                                        choice["finish_reason"]?.jsonPrimitive?.contentOrNull
                                            ?: "unknown"
                                    add(
                                        UIMessageChoice(
                                            index = 0,
                                            delta = parseMessage(message),
                                            message = null,
                                            finishReason = finishReason,
                                        )
                                    )
                                }
                            }
                            val usage = parseTokenUsage(it["usage"] as? JsonObject)

                            val messageChunk = MessageChunk(
                                id = id,
                                model = model,
                                choices = choiceList,
                                usage = usage
                            )
                            emit(messageChunk)
                        }
                }
            } catch (t: Throwable) {
                val response = call.response
                var exception: Throwable

                t.printStackTrace()
                println("[onFailure] 发生错误: ${t::class.qualifiedName} ${t.message} / $response")

                val bodyRaw = response.stringSafe()
                runCatching {
                    if (!bodyRaw.isNullOrEmpty()) {
                        val bodyElement = Json.parseToJsonElement(bodyRaw)
                        println(bodyElement)
                        exception = bodyElement.parseErrorDetail()
                        Logger.i(TAG) { "onFailure: $exception" }
                    }
                }.onFailure { e ->
                    Logger.i(TAG) { "onFailure: failed to parse from $bodyRaw" }
                    e.printStackTrace()
                }
            } finally {
                println("[onCompletion] 关闭eventSource")
            }
        }
    }


    private fun buildChatCompletionRequest(
        messages: List<UIMessage>,
        params: TextGenerationParams,
        providerSetting: ProviderSetting.OpenAI,
        stream: Boolean = false,
    ): JsonObject {
        val host = Url(providerSetting.baseUrl).host
        return buildJsonObject {
            put("model", params.model.modelId)
            put("messages", buildMessages(messages))

            if (isModelAllowTemperature(params.model)) {
                if (params.temperature != null) put("temperature", params.temperature)
                if (params.topP != null) put("top_p", params.topP)
            }
            if (params.maxTokens != null) put("max_tokens", params.maxTokens)

            put("stream", stream)
            if (stream) {
                if (host != "api.mistral.ai") { // mistral 不支持 stream_options
                    put("stream_options", buildJsonObject {
                        put("include_usage", true)
                    })
                }
            }

            // open router适配
            if (host == "openrouter.ai") {
                if (params.model.outputModalities.contains(Modality.IMAGE)) {
                    put("modalities", buildJsonArray {
                        add("image")
                        add("text")
                    })
                }
            }

            if (params.model.abilities.contains(ModelAbility.REASONING)) {
                val level = ReasoningLevel.fromBudgetTokens(params.thinkingBudget)
                when (host) {
                    "openrouter.ai" -> {
                        // https://openrouter.ai/docs/use-cases/reasoning-tokens
                        put("reasoning", buildJsonObject {
                            if (level != ReasoningLevel.AUTO) put("max_tokens", params.thinkingBudget ?: 0)
                            if (!level.isEnabled) {
                                put("enabled", false)
                            }
                        })
                    }

                    "dashscope.aliyuncs.com" -> {
                        // 阿里云百炼
                        // https://bailian.console.aliyun.com/console?tab=doc#/doc/?type=model&url=https%3A%2F%2Fhelp.aliyun.com%2Fdocument_detail%2F2870973.html&renderType=iframe
                        put("enable_thinking", level.isEnabled)
                        if (level != ReasoningLevel.AUTO) put("thinking_budget", params.thinkingBudget ?: 0)
                    }

                    "ark.cn-beijing.volces.com" -> {
                        // 豆包 (火山)
                        put("thinking", buildJsonObject {
                            put("type", if (!level.isEnabled) "disabled" else "enabled")
                        })
                    }

                    "api.mistral.ai" -> {
                        // Mistral 不支持
                    }

                    "chat.intern-ai.org.cn" -> {
                        // 书生
                        // https://internlm.intern-ai.org.cn/api/document?lang=zh
                        put("thinking_mode", level.isEnabled)
                    }

                    "api.siliconflow.cn" -> {
                        // https://docs.siliconflow.cn/cn/userguide/capabilities/reasoning#3-1-api-%E5%8F%82%E6%95%B0
                        val modelId = params.model.modelId
                        if (modelId.contains("DeepSeek-V3.1") || modelId.contains("GLM-4.5") || modelId.contains("Qwen3-8B")) {
                            put("enable_thinking", level.isEnabled)
                        }
                    }

                    "open.bigmodel.cn" -> {
                        put("thinking", buildJsonObject {
                            put("type", if (!level.isEnabled) "disabled" else "enabled")
                        })
                    }

                    else -> {
                        // OpenAI 官方
                        // 文档中，只支持 "low", "medium", "high"
                        if (level != ReasoningLevel.AUTO) {
                            put("reasoning_effort", level.effort)
                        }
                    }
                }
            }

            if (params.model.abilities.contains(ModelAbility.TOOL) && params.tools.isNotEmpty()) {
                putJsonArray("tools") {
                    params.tools.forEach { tool ->
                        add(buildJsonObject {
                            put("type", "function")
                            put("function", buildJsonObject {
                                put("name", tool.name)
                                put("description", tool.description)
                                put(
                                    "parameters",
                                    json.encodeToJsonElement(
                                        tool.parameters()
                                    )
                                )
                            })
                        })
                    }
                }
            }
        }.mergeCustomBody(params.customBody)
    }

    private fun isModelAllowTemperature(model: Model): Boolean {
        return !ModelRegistry.OPENAI_O_MODELS.match(model.modelId) && !ModelRegistry.GPT_5.match(model.modelId)
    }

    private fun buildMessages(messages: List<UIMessage>) = buildJsonArray {
        messages
            .filter {
                it.isValidToUpload()
            }
            .forEachIndexed { index, message ->
                if (message.role == MessageRole.TOOL) {
                    message.getToolResults().forEach { result ->
                        add(buildJsonObject {
                            put("role", "tool")
                            put("name", result.toolName)
                            put("tool_call_id", result.toolCallId)
                            put("content", json.encodeToString(result.content))
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
                                            put("type", "text")
                                            put("text", part.text)
                                        })
                                    }

                                    is UIMessagePart.Image -> {
                                        add(buildJsonObject {
                                            part.encodeBase64().onSuccess {
                                                put("type", "image_url")
                                                put("image_url", buildJsonObject {
                                                    put("url", it)
                                                })
                                            }.onFailure {
                                                it.printStackTrace()
                                                println("encode image failed: ${part.url}")

                                                put("type", "text")
                                                put("text", "")
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

                    // tool_calls
                    message.getToolCalls()
                        .takeIf { it.isNotEmpty() }
                        ?.let { toolCalls ->
                            put("tool_calls", buildJsonArray {
                                toolCalls.forEach { toolCall ->
                                    add(buildJsonObject {
                                        put("id", toolCall.toolCallId)
                                        put("type", "function")
                                        put("function", buildJsonObject {
                                            put("name", toolCall.toolName)
                                            put("arguments", toolCall.arguments)
                                        })
                                    })
                                }
                            })
                        }
                })
            }
    }

    private fun parseMessage(jsonObject: JsonObject): UIMessage {
        val role = MessageRole.valueOf(
            jsonObject["role"]?.jsonPrimitive?.contentOrNull?.uppercase() ?: "ASSISTANT"
        )

        // 也许支持其他模态的输出content? 暂时只支持文本吧
        val content = jsonObject["content"]?.jsonPrimitive?.contentOrNull ?: ""
        val reasoning = jsonObject["reasoning_content"]?.jsonPrimitive?.contentOrNull
            ?: jsonObject["reasoning"]?.jsonPrimitive?.contentOrNull
        val toolCalls = jsonObject["tool_calls"] as? JsonArray ?: JsonArray(emptyList())
        val images = jsonObject["images"] as? JsonArray ?: JsonArray(emptyList())

        return UIMessage(
            role = role,
            parts = buildList {
                if (!reasoning.isNullOrEmpty()) {
                    add(
                        UIMessagePart.Reasoning(
                            reasoning = reasoning,
                            createdAt = Clock.System.now(),
                            finishedAt = null
                        )
                    )
                }
                toolCalls.forEach { toolCalls ->
                    val type = toolCalls.jsonObject["type"]?.jsonPrimitive?.contentOrNull
                    if (!type.isNullOrEmpty() && type != "function") error("tool call type not supported: $type")
                    val toolCallId = toolCalls.jsonObject["id"]?.jsonPrimitive?.contentOrNull
                    val toolName =
                        toolCalls.jsonObject["function"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull
                    val arguments =
                        toolCalls.jsonObject["function"]?.jsonObject?.get("arguments")?.jsonPrimitive?.contentOrNull
                    add(
                        UIMessagePart.ToolCall(
                            toolCallId = toolCallId ?: "",
                            toolName = toolName ?: "",
                            arguments = arguments ?: ""
                        )
                    )
                }
                add(UIMessagePart.Text(content))
                images.forEach { image ->
                    val imageObject = image.jsonObjectOrNull ?: return@forEach
                    val type = imageObject["type"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                    if (type != "image_url") return@forEach
                    val url = imageObject["image_url"]?.jsonObjectOrNull?.get("url")?.jsonPrimitive?.contentOrNull
                        ?: return@forEach
                    require(url.startsWith("data:image")) { "Only data uri is supported" }
                    add(UIMessagePart.Image(url.substringAfter("data:image/png;base64,")))
                }
            },
            annotations = parseAnnotations(
                jsonObject["annotations"]?.jsonArray ?: JsonArray(
                    emptyList()
                )
            ),
        )
    }

    private fun parseAnnotations(jsonArray: JsonArray): List<UIMessageAnnotation> {
        return jsonArray.map { element ->
            val type =
                element.jsonObject["type"]?.jsonPrimitive?.contentOrNull ?: error("type is null")
            when (type) {
                "url_citation" -> {
                    UIMessageAnnotation.UrlCitation(
                        title = element.jsonObject["url_citation"]?.jsonObject?.get("title")?.jsonPrimitive?.contentOrNull
                            ?: "",
                        url = element.jsonObject["url_citation"]?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull
                            ?: "",
                    )
                }

                else -> error("unknown annotation type: $type")
            }
        }
    }

    private fun parseTokenUsage(jsonObject: JsonObject?): TokenUsage? {
        if (jsonObject == null) return null
        return TokenUsage(
            promptTokens = jsonObject["prompt_tokens"]?.jsonPrimitive?.intOrNull ?: 0,
            completionTokens = jsonObject["completion_tokens"]?.jsonPrimitive?.intOrNull ?: 0,
            totalTokens = jsonObject["total_tokens"]?.jsonPrimitive?.intOrNull ?: 0,
            cachedTokens = jsonObject["prompt_tokens_details"]?.jsonObjectOrNull?.get("cached_tokens")?.jsonPrimitive?.intOrNull
                ?: 0
        )
    }

    private fun List<UIMessagePart>.isOnlyTextPart(): Boolean {
        val gonnaSend = filter { it is UIMessagePart.Text || it is UIMessagePart.Image }.size
        val texts = filter { it is UIMessagePart.Text }.size
        return gonnaSend == texts && texts == 1
    }
}
