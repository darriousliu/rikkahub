package me.rerere.ai.provider.providers

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.sse
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
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
import kotlinx.serialization.json.putJsonObject
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.TokenUsage
import me.rerere.ai.provider.BuiltInTools
import me.rerere.ai.provider.ImageGenerationParams
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.Provider
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.provider.providers.vertex.ServiceAccountTokenProvider
import me.rerere.ai.ui.ImageAspectRatio
import me.rerere.ai.ui.ImageGenerationItem
import me.rerere.ai.ui.ImageGenerationResult
import me.rerere.ai.ui.MessageChunk
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageAnnotation
import me.rerere.ai.ui.UIMessageChoice
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.util.KeyRoulette
import me.rerere.ai.util.configureClientWithProxy
import me.rerere.ai.util.configureReferHeaders
import me.rerere.ai.util.encodeBase64
import me.rerere.ai.util.json
import me.rerere.ai.util.mergeCustomBody
import me.rerere.ai.util.removeElements
import me.rerere.ai.util.stringSafe
import me.rerere.ai.util.toHeaders
import me.rerere.ai.util.unescapeJson
import me.rerere.common.http.jsonPrimitiveOrNull
import kotlin.time.Clock
import kotlin.uuid.Uuid

private const val TAG = "GoogleProvider"

class GoogleProvider(private val client: HttpClient) : Provider<ProviderSetting.Google> {
    private val keyRoulette = KeyRoulette.default()
    private val serviceAccountTokenProvider by lazy {
        ServiceAccountTokenProvider(client)
    }

    private fun buildUrl(providerSetting: ProviderSetting.Google, path: String): Url {
        return if (!providerSetting.vertexAI) {
            val key = keyRoulette.next(providerSetting.apiKey)
            URLBuilder("${providerSetting.baseUrl}/$path").apply {
                parameters.append("key", key)
            }.build()
        } else {
            Url("https://aiplatform.googleapis.com/v1/projects/${providerSetting.projectId}/locations/${providerSetting.location}/$path")
        }
    }

    private suspend fun transformRequest(
        providerSetting: ProviderSetting.Google,
        request: HttpRequestBuilder
    ): HttpRequestBuilder {
        return if (providerSetting.vertexAI) {
            val accessToken = serviceAccountTokenProvider.fetchAccessToken(
                serviceAccountEmail = providerSetting.serviceAccountEmail.trim(),
                privateKeyPem = providerSetting.privateKey.trim().unescapeJson(),
            )
            HttpRequestBuilder().apply {
                takeFrom(request)
                header("Authorization", "Bearer $accessToken")
            }
        } else {
            HttpRequestBuilder().takeFrom(request)
        }
    }

    override suspend fun listModels(providerSetting: ProviderSetting.Google): List<Model> =
        withContext(Dispatchers.IO) {
            val url = buildUrl(providerSetting = providerSetting, path = "models")
            val request = transformRequest(
                providerSetting = providerSetting,
                request = HttpRequestBuilder().apply {
                    url(url)
                    method = HttpMethod.Get
                }
            )
            val response = client.configureClientWithProxy(providerSetting.proxy).request(request)
            if (response.status.isSuccess()) {
                val body = response.stringSafe() ?: error("empty body")
                Logger.d(TAG) { "listModels: $body" }
                val bodyObject = json.parseToJsonElement(body).jsonObject
                val models = bodyObject["models"]?.jsonArray ?: return@withContext emptyList()

                models.mapNotNull {
                    val modelObject = it.jsonObject

                    // 忽略非chat/embedding模型
                    val supportedGenerationMethods =
                        modelObject["supportedGenerationMethods"]!!.jsonArray
                            .map { method -> method.jsonPrimitive.content }
                    if ("generateContent" !in supportedGenerationMethods && "embedContent" !in supportedGenerationMethods) {
                        return@mapNotNull null
                    }

                    Model(
                        modelId = modelObject["name"]!!.jsonPrimitive.content.substringAfter("/"),
                        displayName = modelObject["displayName"]!!.jsonPrimitive.content,
                        type = if ("generateContent" in supportedGenerationMethods) ModelType.CHAT else ModelType.EMBEDDING,
                    )
                }
            } else {
                emptyList()
            }
        }

    override suspend fun generateText(
        providerSetting: ProviderSetting.Google,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): MessageChunk = withContext(Dispatchers.IO) {
        val requestBody = buildCompletionRequestBody(messages, params)

        val url = buildUrl(
            providerSetting = providerSetting,
            path = if (providerSetting.vertexAI) {
                "publishers/google/models/${params.model.modelId}:generateContent"
            } else {
                "models/${params.model.modelId}:generateContent"
            }
        )

        val request = transformRequest(
            providerSetting = providerSetting,
            request = HttpRequestBuilder().apply {
                url(url)
                headers { appendAll(params.customHeaders.toHeaders()) }
                method = HttpMethod.Post
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(requestBody))
                configureReferHeaders(providerSetting.baseUrl)
            }
        )

        val response = client.configureClientWithProxy(providerSetting.proxy).request(request)
        if (!response.status.isSuccess()) {
            throw Exception("Failed to get response: ${response.status.value} ${response.stringSafe()}")
        }

        val bodyStr = response.stringSafe().orEmpty()
        val bodyJson = json.parseToJsonElement(bodyStr).jsonObject

        val candidates = bodyJson["candidates"]!!.jsonArray
        val usage = bodyJson["usageMetadata"]!!.jsonObject

        val messageChunk = MessageChunk(
            id = Uuid.random().toString(),
            model = params.model.modelId,
            choices = candidates.map { candidate ->
                UIMessageChoice(
                    message = parseMessage(candidate.jsonObject),
                    index = 0,
                    finishReason = null,
                    delta = null
                )
            },
            usage = parseUsageMeta(usage)
        )

        messageChunk
    }

    override suspend fun streamText(
        providerSetting: ProviderSetting.Google,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): Flow<MessageChunk> = flow {
        val requestBody = buildCompletionRequestBody(messages, params)

        val url = URLBuilder(
            buildUrl(
                providerSetting = providerSetting,
                path = if (providerSetting.vertexAI) {
                    "publishers/google/models/${params.model.modelId}:streamGenerateContent"
                } else {
                    "models/${params.model.modelId}:streamGenerateContent"
                }
            )
        ).apply {
            parameters.append("alt", "sse")
        }.build()

        val request = transformRequest(
            providerSetting = providerSetting,
            request = HttpRequestBuilder().apply {
                url(url)
                headers { appendAll(params.customHeaders.toHeaders()) }
                method = HttpMethod.Post
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(requestBody))
                configureReferHeaders(providerSetting.baseUrl)
            }
        )

        Logger.i(TAG) { "streamText: ${json.encodeToString(requestBody)}" }

        client.configureClientWithProxy(providerSetting.proxy).sse({ takeFrom(request) }) {
            try {
                incoming.collect { event ->
                    Logger.i(TAG) { "onEvent: ${event.data}" }

                    try {
                        val jsonData = json.parseToJsonElement(event.data.orEmpty()).jsonObject
                        val candidates = jsonData["candidates"]?.jsonArray ?: return@collect
                        if (candidates.isEmpty()) return@collect
                        val usage = parseUsageMeta(jsonData["usageMetadata"] as? JsonObject)
                        val messageChunk = MessageChunk(
                            id = Uuid.random().toString(),
                            model = params.model.modelId,
                            choices = candidates.mapIndexed { index, candidate ->
                                val candidateObj = candidate.jsonObject
                                val content = candidateObj["content"]?.jsonObject
                                val groundingMetadata = candidateObj["groundingMetadata"]?.jsonObject
                                val finishReason =
                                    candidateObj["finishReason"]?.jsonPrimitive?.contentOrNull

                                val message = content?.let {
                                    parseMessage(buildJsonObject {
                                        put("role", JsonPrimitive("model"))
                                        put("content", it)
                                        groundingMetadata?.let { groundingMetadata ->
                                            put("groundingMetadata", groundingMetadata)
                                        }
                                    })
                                }

                                UIMessageChoice(
                                    index = index,
                                    delta = message,
                                    message = null,
                                    finishReason = finishReason
                                )
                            },
                            usage = usage
                        )

                        emit(messageChunk)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        println("[onEvent] 解析错误: ${event.data}")
                    }
                }
            } catch (t: Throwable) {
                val response = call.response
                var exception = t

                t.printStackTrace()
                println("[onFailure] 发生错误: ${t.message}")

                runCatching {
                    val bodyStr = response.stringSafe()
                    if (!bodyStr.isNullOrEmpty()) {
                        val bodyElement = json.parseToJsonElement(bodyStr)
                        println(bodyElement)
                        if (bodyElement is JsonObject) {
                            exception = Exception(
                                bodyElement["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
                                    ?: "unknown"
                            )
                        }
                    } else {
                        exception = Exception("Unknown error: ${response.status.value}")
                    }
                    exception.printStackTrace()
                }.onFailure { e ->
                    exception = e
                    exception.printStackTrace()
                }
            } finally {
                println("[awaitClose] 关闭eventSource")
            }
        }
    }

    private fun buildCompletionRequestBody(
        messages: List<UIMessage>,
        params: TextGenerationParams
    ): JsonObject = buildJsonObject {
        // System message if available
        val systemMessage = messages.firstOrNull { it.role == MessageRole.SYSTEM }
        if (systemMessage != null && !params.model.outputModalities.contains(Modality.IMAGE)) {
            put("systemInstruction", buildJsonObject {
                putJsonArray("parts") {
                    add(buildJsonObject {
                        put(
                            "text",
                            systemMessage.parts.filterIsInstance<UIMessagePart.Text>()
                                .joinToString { it.text })
                    })
                }
            })
        }

        // Generation config
        put("generationConfig", buildJsonObject {
            if (params.temperature != null) put("temperature", params.temperature)
            if (params.topP != null) put("topP", params.topP)
            if (params.maxTokens != null) put("maxOutputTokens", params.maxTokens)
            if (params.model.outputModalities.contains(Modality.IMAGE)) {
                put("responseModalities", buildJsonArray {
                    add(JsonPrimitive("TEXT"))
                    add(JsonPrimitive("IMAGE"))
                })
            }
            if (params.model.abilities.contains(ModelAbility.REASONING)) {
                put("thinkingConfig", buildJsonObject {
                    put("includeThoughts", true)

                    val isGeminiPro =
                        params.model.modelId.contains(Regex("2\\.5.*pro", RegexOption.IGNORE_CASE))

                    when (params.thinkingBudget) {
                        null, -1 -> {} // 如果是自动，不设置thinkingBudget参数

                        0 -> {
                            // disable thinking if not gemini pro
                            if (!isGeminiPro) {
                                put("thinkingBudget", 0)
                                put("includeThoughts", false)
                            }
                        }

                        else -> put("thinkingBudget", params.thinkingBudget)
                    }
                })
            }
        })

        // Contents (user messages)
        put(
            "contents",
            buildContents(messages)
        )

        // Tools
        if (params.tools.isNotEmpty() && params.model.abilities.contains(ModelAbility.TOOL)) {
            put("tools", buildJsonArray {
                add(buildJsonObject {
                    put("functionDeclarations", buildJsonArray {
                        params.tools.forEach { tool ->
                            add(buildJsonObject {
                                put("name", JsonPrimitive(tool.name))
                                put("description", JsonPrimitive(tool.description))
                                put(
                                    key = "parameters",
                                    element = json.encodeToJsonElement(tool.parameters())
                                        .removeElements(
                                            listOf(
                                                "const",
                                                "exclusiveMaximum",
                                                "exclusiveMinimum",
                                                "format",
                                                "additionalProperties",
                                                "enum",
                                            )
                                        )
                                )
                            })
                        }
                    })
                })
            })
        }
        // Model BuiltIn Tools
        // 目前不能和工具调用兼容
        if (params.model.tools.isNotEmpty()) {
            put("tools", buildJsonArray {
                params.model.tools.forEach { builtInTool ->
                    when (builtInTool) {
                        BuiltInTools.Search -> {
                            add(buildJsonObject {
                                put("google_search", buildJsonObject {})
                            })
                        }

                        BuiltInTools.UrlContext -> {
                            add(buildJsonObject {
                                put("url_context", buildJsonObject {})
                            })
                        }
                    }
                }
            })
        }

        // Safety Settings
        putJsonArray("safetySettings") {
            add(buildJsonObject {
                put("category", "HARM_CATEGORY_HARASSMENT")
                put("threshold", "OFF")
            })
            add(buildJsonObject {
                put("category", "HARM_CATEGORY_HATE_SPEECH")
                put("threshold", "OFF")
            })
            add(buildJsonObject {
                put("category", "HARM_CATEGORY_SEXUALLY_EXPLICIT")
                put("threshold", "OFF")
            })
            add(buildJsonObject {
                put("category", "HARM_CATEGORY_DANGEROUS_CONTENT")
                put("threshold", "OFF")
            })
            add(buildJsonObject {
                put("category", "HARM_CATEGORY_CIVIC_INTEGRITY")
                put("threshold", "OFF")
            })
        }
    }.mergeCustomBody(params.customBody)

    private fun commonRoleToGoogleRole(role: MessageRole): String {
        return when (role) {
            MessageRole.USER -> "user"
            MessageRole.SYSTEM -> "system"
            MessageRole.ASSISTANT -> "model"
            MessageRole.TOOL -> "user" // google api中, tool结果是用户role发送的
        }
    }

    private fun googleRoleToCommonRole(role: String): MessageRole {
        return when (role) {
            "user" -> MessageRole.USER
            "system" -> MessageRole.SYSTEM
            "model" -> MessageRole.ASSISTANT
            else -> error("Unknown role $role")
        }
    }

    private fun parseMessage(message: JsonObject): UIMessage {
        val role = googleRoleToCommonRole(
            message["role"]?.jsonPrimitive?.contentOrNull ?: "model"
        )
        val content = message["content"]?.jsonObject ?: error("No content")
        val parts = content["parts"]?.jsonArray?.map { part ->
            parseMessagePart(part.jsonObject)
        } ?: emptyList()

        val groundingMetadata = message["groundingMetadata"]?.jsonObject
        Logger.i(TAG) { "parseMessage: $groundingMetadata" }
        val annotations = parseSearchGroundingMetadata(groundingMetadata)

        return UIMessage(
            role = role,
            parts = parts,
            annotations = annotations
        )
    }

    private fun parseSearchGroundingMetadata(jsonObject: JsonObject?): List<UIMessageAnnotation> {
        if (jsonObject == null) return emptyList()
        val groundingChunks = jsonObject["groundingChunks"]?.jsonArray ?: emptyList()
        val chunks = groundingChunks.mapNotNull { chunk ->
            val web = chunk.jsonObject["web"]?.jsonObject ?: return@mapNotNull null
            val uri = web["uri"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val title = web["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            UIMessageAnnotation.UrlCitation(
                title = title,
                url = uri
            )
        }
        Logger.i(TAG) { "parseSearchGroundingMetadata: $chunks" }
        return chunks
    }

    private fun parseMessagePart(jsonObject: JsonObject): UIMessagePart {
        return when {
            jsonObject.containsKey("text") -> {
                val thought = jsonObject["thought"]?.jsonPrimitive?.booleanOrNull ?: false
                val text = jsonObject["text"]?.jsonPrimitive?.content ?: ""
                if (thought) UIMessagePart.Reasoning(
                    reasoning = text,
                    createdAt = Clock.System.now(),
                    finishedAt = null
                ) else UIMessagePart.Text(text)
            }

            jsonObject.containsKey("functionCall") -> {
                UIMessagePart.ToolCall(
                    toolCallId = "",
                    toolName = jsonObject["functionCall"]!!.jsonObject["name"]!!.jsonPrimitive.content,
                    arguments = json.encodeToString(jsonObject["functionCall"]!!.jsonObject["args"])
                )
            }

            jsonObject.containsKey("inlineData") -> {
                val inlineData = jsonObject["inlineData"]!!.jsonObject
                val mime = inlineData["mimeType"]?.jsonPrimitive?.content ?: "image/png"
                val data = inlineData["data"]?.jsonPrimitive?.content ?: ""
                require(mime.startsWith("image/")) {
                    "Only image mime type is supported"
                }
                UIMessagePart.Image(data)
            }

            else -> error("unknown message part type: $jsonObject")
        }
    }

    private fun buildContents(messages: List<UIMessage>): JsonArray {
        return buildJsonArray {
            messages
                .filter { it.role != MessageRole.SYSTEM && it.isValidToUpload() }
                .forEachIndexed { index, message ->
                    add(buildJsonObject {
                        put("role", commonRoleToGoogleRole(message.role))
                        putJsonArray("parts") {
                            for (part in message.parts) {
                                when (part) {
                                    is UIMessagePart.Text -> {
                                        add(buildJsonObject {
                                            put("text", part.text)
                                        })
                                    }

                                    is UIMessagePart.Image -> {
                                        part.encodeBase64(false).onSuccess { base64Data ->
                                            add(buildJsonObject {
                                                put("inline_data", buildJsonObject {
                                                    put("mime_type", "image/png")
                                                    put("data", base64Data)
                                                })
                                            })
                                        }
                                    }

                                    is UIMessagePart.ToolCall -> {
                                        add(buildJsonObject {
                                            put("functionCall", buildJsonObject {
                                                put("name", part.toolName)
                                                put("args", json.parseToJsonElement(part.arguments))
                                            })
                                        })
                                    }

                                    is UIMessagePart.ToolResult -> {
                                        add(buildJsonObject {
                                            put("functionResponse", buildJsonObject {
                                                put("name", part.toolName)
                                                put("response", buildJsonObject {
                                                    put("result", part.content)
                                                })
                                            })
                                        })
                                    }

                                    else -> {
                                        // Unsupported part type
                                    }
                                }
                            }
                        }
                    })
                }
        }
    }

    private fun parseUsageMeta(jsonObject: JsonObject?): TokenUsage? {
        if (jsonObject == null) {
            return null
        }
        val promptTokens = jsonObject["promptTokenCount"]?.jsonPrimitiveOrNull?.intOrNull ?: 0
        val thoughtTokens = jsonObject["thoughtsTokenCount"]?.jsonPrimitiveOrNull?.intOrNull ?: 0
        val cachedTokens = jsonObject["cachedContentTokenCount"]?.jsonPrimitiveOrNull?.intOrNull ?: 0
        val candidatesTokens = jsonObject["candidatesTokenCount"]?.jsonPrimitiveOrNull?.intOrNull ?: 0
        val totalTokens = jsonObject["totalTokenCount"]?.jsonPrimitiveOrNull?.intOrNull ?: 0
        return TokenUsage(
            promptTokens = promptTokens,
            completionTokens = candidatesTokens + thoughtTokens,
            totalTokens = totalTokens,
            cachedTokens = cachedTokens
        )
    }

    override suspend fun generateImage(
        providerSetting: ProviderSetting,
        params: ImageGenerationParams
    ): ImageGenerationResult = withContext(Dispatchers.IO) {
        require(providerSetting is ProviderSetting.Google) {
            "Expected Google provider setting"
        }

        val requestBody = buildJsonObject {
            putJsonArray("instances") {
                add(buildJsonObject {
                    put("prompt", params.prompt)
                })
            }
            putJsonObject("parameters") {
                put("sampleCount", params.numOfImages)
                put(
                    "aspectRatio", when (params.aspectRatio) {
                        ImageAspectRatio.SQUARE -> "1:1"
                        ImageAspectRatio.LANDSCAPE -> "16:9"
                        ImageAspectRatio.PORTRAIT -> "9:16"
                    }
                )
            }
        }.mergeCustomBody(params.customBody)

        val url = buildUrl(
            providerSetting = providerSetting,
            path = if (providerSetting.vertexAI) {
                "publishers/google/models/${params.model.modelId}:predict"
            } else {
                "models/${params.model.modelId}:predict"
            }
        )

        val request = transformRequest(
            providerSetting = providerSetting,
            request = HttpRequestBuilder().apply {
                url(url)
                headers {
                    appendAll(params.customHeaders.toHeaders())
                }
                method = HttpMethod.Post
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(requestBody))
                configureReferHeaders(providerSetting.baseUrl)
            }
        )

        val response = client.configureClientWithProxy(providerSetting.proxy).request(request)
        if (!response.status.isSuccess()) {
            error("Failed to generate image: ${response.status.value} ${response.stringSafe()}")
        }

        val bodyStr = response.bodyAsText()
        val bodyJson = json.parseToJsonElement(bodyStr).jsonObject

        val predictions = bodyJson["predictions"]?.jsonArray ?: error("No predictions in response")

        val items = predictions.mapNotNull { prediction ->
            val predictionObj = prediction.jsonObject
            val bytesBase64Encoded = predictionObj["bytesBase64Encoded"]?.jsonPrimitive?.contentOrNull

            if (bytesBase64Encoded != null) {
                ImageGenerationItem(
                    data = bytesBase64Encoded,
                    mimeType = "image/png"
                )
            } else null
        }

        ImageGenerationResult(items = items)
    }
}
