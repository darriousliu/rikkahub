package me.rerere.ai.provider.providers

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.append
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import me.rerere.common.logging.RikkaLog as Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.io.write
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import me.rerere.ai.provider.EmbeddingGenerationParams
import me.rerere.ai.provider.EmbeddingGenerationResult
import me.rerere.ai.provider.ImageEditParams
import me.rerere.ai.provider.ImageGenerationParams
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.Provider
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.provider.providers.openai.ChatCompletionsAPI
import me.rerere.ai.provider.providers.openai.ResponseAPI
import me.rerere.ai.provider.providers.openai.configureOpenAIRequest
import me.rerere.ai.provider.providers.openai.toOpenAIJsonContent
import me.rerere.ai.ui.ImageGenerationItem
import me.rerere.ai.ui.MessageChunk
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.util.KeyRoulette
import me.rerere.ai.util.json
import me.rerere.ai.util.mergeCustomBody
import me.rerere.common.http.getByKey
import java.io.File
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private const val TAG = "OpenAIProvider"

class OpenAIProvider(
    private val client: HttpClient,
    private val keyRoulette: KeyRoulette = KeyRoulette.default(),
) : Provider<ProviderSetting.OpenAI> {
    private val chatCompletionsAPI = ChatCompletionsAPI(client = client, keyRoulette = keyRoulette)
    private val responseAPI = ResponseAPI(client = client, keyRoulette = keyRoulette)


    override suspend fun listModels(providerSetting: ProviderSetting.OpenAI): List<Model> {
        val response = client.get("${providerSetting.baseUrl}/models") {
            configureOpenAIRequest(providerSetting, keyRoulette, emptyList())
        }
        val bodyStr = response.bodyAsText()
        if (!response.status.isSuccess()) {
            error("Failed to get models: ${response.status.value} $bodyStr")
        }
        val data = json.parseToJsonElement(bodyStr).jsonObject["data"]?.jsonArray ?: return emptyList()
        return data.mapNotNull { modelJson ->
            val id = modelJson.jsonObject["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            Model(modelId = id, displayName = id)
        }
    }

    override suspend fun getBalance(providerSetting: ProviderSetting.OpenAI): String {
        val url = if (providerSetting.balanceOption.apiPath.startsWith("http")) {
            providerSetting.balanceOption.apiPath
        } else {
            "${providerSetting.baseUrl}${providerSetting.balanceOption.apiPath}"
        }
        val response = client.get(url) {
            configureOpenAIRequest(providerSetting, keyRoulette, emptyList())
        }
        val bodyStr = response.bodyAsText()
        if (!response.status.isSuccess()) {
            error("Failed to get balance: ${response.status.value} $bodyStr")
        }
        val bodyJson = json.parseToJsonElement(bodyStr).jsonObject
        val value = bodyJson.getByKey(providerSetting.balanceOption.resultPath)
        val digitalValue = value.toFloatOrNull()
        return if (digitalValue != null) {
            "%.2f".format(digitalValue)
        } else {
            value
        }
    }

    override suspend fun streamText(
        providerSetting: ProviderSetting.OpenAI,
        messages: List<UIMessage>,
        params: TextGenerationParams
    ): Flow<MessageChunk> = if (providerSetting.useResponseApi) {
        responseAPI.streamText(
            providerSetting = providerSetting,
            messages = messages,
            params = params
        )
    } else {
        chatCompletionsAPI.streamText(
            providerSetting = providerSetting,
            messages = messages,
            params = params
        )
    }

    override suspend fun generateText(
        providerSetting: ProviderSetting.OpenAI,
        messages: List<UIMessage>,
        params: TextGenerationParams
    ): MessageChunk = if (providerSetting.useResponseApi) {
        responseAPI.generateText(
            providerSetting = providerSetting,
            messages = messages,
            params = params
        )
    } else {
        chatCompletionsAPI.generateText(
            providerSetting = providerSetting,
            messages = messages,
            params = params
        )
    }

    override suspend fun generateEmbedding(
        providerSetting: ProviderSetting.OpenAI,
        params: EmbeddingGenerationParams
    ): EmbeddingGenerationResult {
        require(params.input.isNotEmpty()) { "Embedding input cannot be empty" }

        val requestBody = buildJsonObject {
                put("model", params.model.modelId)
                if (params.input.size == 1) {
                    put("input", params.input.first())
                } else {
                    putJsonArray("input") {
                        params.input.forEach { add(JsonPrimitive(it)) }
                    }
                }
                params.dimensions?.let { put("dimensions", it) }
            }.mergeCustomBody(params.customBody)
        val response = client.post("${providerSetting.baseUrl}/embeddings") {
            configureOpenAIRequest(providerSetting, keyRoulette, params.customHeaders)
            setBody(requestBody.toOpenAIJsonContent())
        }
        val bodyStr = response.bodyAsText()
        if (!response.status.isSuccess()) {
            error("Failed to generate embedding: ${response.status.value} $bodyStr")
        }
        val bodyJson = json.parseToJsonElement(bodyStr).jsonObject
        val data = bodyJson["data"]?.jsonArray ?: error("No data in response")
        val model = bodyJson["model"]?.jsonPrimitive?.contentOrNull ?: params.model.modelId

        val embeddings = data.map { embeddingJson ->
            val embeddingArray = embeddingJson.jsonObject["embedding"]?.jsonArray
                ?: error("No embedding in response")
            embeddingArray.map { it.jsonPrimitive.content.toFloat() }
        }

        return EmbeddingGenerationResult(
            model = model,
            embeddings = embeddings
        )
    }

    override suspend fun generateImage(
        providerSetting: ProviderSetting,
        params: ImageGenerationParams
    ): Flow<ImageGenerationItem> = flow {
        require(providerSetting is ProviderSetting.OpenAI) {
            "Expected OpenAI provider setting"
        }

        val requestBody = buildJsonObject {
                put("model", params.model.modelId)
                put("prompt", params.prompt)
                put("n", params.numOfImages)
                
                val isGrok = providerSetting.baseUrl.contains("x.ai", ignoreCase = true) || 
                    params.model.modelId.contains("grok", ignoreCase = true)
                
                if (params.size.isNotBlank() && !isGrok) {
                    put("size", params.size)
                }
            }.mergeCustomBody(params.customBody)

        Log.i(TAG, "generateImage: ${json.encodeToString(requestBody)}")
        val response = client.post("${providerSetting.baseUrl}/images/generations") {
            configureOpenAIRequest(providerSetting, keyRoulette, params.customHeaders)
            setBody(requestBody.toOpenAIJsonContent())
        }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            error("Failed to generate image: ${response.status.value} $body")
        }
        parseImageResponse(body).forEach { emit(it) }
    }

    override suspend fun editImage(
        providerSetting: ProviderSetting,
        params: ImageEditParams
    ): Flow<ImageGenerationItem> = flow {
        require(providerSetting is ProviderSetting.OpenAI) {
            "Expected OpenAI provider setting"
        }
        require(params.images.isNotEmpty()) {
            "At least one image is required"
        }

        val imageFieldName = if (params.images.size == 1) "image" else "image[]"
        val multipart = MultiPartFormDataContent(
            formData {
                append("model", params.model.modelId)
                append("prompt", params.prompt)
                append("n", params.numOfImages)
                if (params.size.isNotBlank()) append("size", params.size)
                params.images.forEach { path ->
                    val imageFile = File(path)
                    require(imageFile.exists()) { "Image file does not exist: $path" }
                    require(imageFile.extension.lowercase() in SUPPORTED_EDIT_IMAGE_EXTENSIONS) {
                        "Unsupported image file type for OpenAI edit: ${imageFile.extension}"
                    }
                    append(
                        key = imageFieldName,
                        filename = imageFile.name,
                        contentType = ContentType.parse(imageFile.imageMediaType()),
                        size = imageFile.length(),
                    ) {
                        write(imageFile.readBytes())
                    }
                }
                params.customBody.forEach { customBody ->
                    val value = when (val element = customBody.value) {
                        is JsonPrimitive -> element.contentOrNull ?: element.toString()
                        else -> element.toString()
                    }
                    append(customBody.key, value)
                }
            }
        )
        val response = client.post("${providerSetting.baseUrl}/images/edits") {
            configureOpenAIRequest(providerSetting, keyRoulette, params.customHeaders)
            setBody(multipart)
        }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            error("Failed to edit image: ${response.status.value} $body")
        }
        parseImageResponse(body).forEach { emit(it) }
    }

    private suspend fun parseImageResponse(bodyStr: String): List<ImageGenerationItem> {
        val body = json.parseToJsonElement(bodyStr).jsonObject
        val defaultFormat = body["output_format"]?.jsonPrimitive?.contentOrNull ?: "png"
        val data = body["data"]?.jsonArray ?: error("No data in image response")
        return data.map { element ->
            val obj = element.jsonObject
            val b64Json = obj["b64_json"]?.jsonPrimitive?.contentOrNull
            if (b64Json != null) {
                val outputFormat = obj["output_format"]?.jsonPrimitive?.contentOrNull ?: defaultFormat
                ImageGenerationItem(
                    data = b64Json,
                    mimeType = outputFormat.toImageMimeType(),
                )
            } else {
                val url = obj["url"]?.jsonPrimitive?.contentOrNull
                    ?: error("No b64_json or url in image response")
                downloadImageAsBase64(url)
            }
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun downloadImageAsBase64(url: String): ImageGenerationItem {
        val response = client.get(url)
        if (!response.status.isSuccess()) {
            error("Failed to download generated image: ${response.status.value} ${response.bodyAsText()}")
        }
        val mimeType = response.headers[HttpHeaders.ContentType] ?: "image/png"
        val base64 = Base64.encode(response.body<ByteArray>())

        return ImageGenerationItem(
            data = base64,
            mimeType = mimeType
        )
    }

    private fun File.imageMediaType(): String = when (extension.lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        else -> "image/png"
    }

    private fun String.toImageMimeType(): String = when (lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        else -> "image/png"
    }

    companion object {
        private val SUPPORTED_EDIT_IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp")
    }
}
