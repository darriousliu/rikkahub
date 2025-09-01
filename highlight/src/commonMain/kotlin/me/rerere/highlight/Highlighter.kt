package me.rerere.highlight

import co.touchlab.kermit.Logger
import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.JsObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.common.utils.ThreadManager
import me.rerere.highlight.HighlightToken.Token.StringContent
import rikkahub.highlight.generated.resources.Res
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class Highlighter() {
    init {
        ThreadManager.runInBackground {
            QuickJs.Companion

            context // init context
        }
    }

    private val script: String by lazy {
        runBlocking {
            Res.readBytes("files/prism.js").decodeToString()
        }
    }

    private val context: QuickJs by lazy {
        runBlocking {
            QuickJs.create(Dispatchers.Default).also {
                it.maxStackSize = 1024 * 1024 // 1MB
                it.evaluate<Boolean>(script)
            }
        }
    }

    suspend fun highlight(code: String, language: String) = suspendCancellableCoroutine { continuation ->
        ThreadManager.runInBackground {
            runCatching {
                val codeJson = Json.encodeToString(code)
                val languageJson = Json.encodeToString(language)

                val result = runBlocking {
                    context.evaluate<Any>(
                        code = "highlight($codeJson, $languageJson)",
                    )
                }
                require(result is List<*>) {
                    "highlight result must be an array"
                }
                val tokens = arrayListOf<HighlightToken>()
                for (i in 0 until result.size) {
                    when (val element = result[i]) {
                        is String -> tokens.add(
                            HighlightToken.Plain(
                                content = element,
                            )
                        )

                        is JsObject -> {
                            val token = format.decodeFromString(
                                HighlightTokenSerializer,
                                format.encodeToString(JsObjectSerializer, element)
                            )
                            tokens.add(token)
                        }

                        else -> error("Unknown type: ${element!!::class.qualifiedName}")
                    }
                }
//            result.release()
                continuation.resume(tokens)
            }.onFailure {
                Logger.e("Highlighter") { "language:  $language\ncode: $code" }
                if (continuation.isActive) {
                    continuation.resumeWithException(it)
                }
            }.getOrThrow()
        }
    }

    fun destroy() {
        context.close()
    }
}

internal val format by lazy {
    Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }
}

sealed class HighlightToken {
    data class Plain(
        val content: String,
    ) : HighlightToken()

    @Serializable
    sealed class Token() : HighlightToken() {
        @Serializable
        data class StringContent(
            val content: String,
            val type: String,
            val length: Int,
        ) : Token()

        @Serializable
        data class StringListContent(
            val content: List<String>,
            val type: String,
            val length: Int,
        ) : Token()

        @Serializable
        data class Nested(
            val content: List<Token>,
            val type: String,
            val length: Int,
        ) : Token()
    }
}

object HighlightTokenSerializer : KSerializer<HighlightToken.Token> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("HighlightToken.Token")

    override fun serialize(
        encoder: Encoder,
        value: HighlightToken.Token
    ) {
        // not used
    }

    override fun deserialize(decoder: Decoder): HighlightToken.Token {
        val jsonDecoder = decoder as JsonDecoder
        val jsonObject = jsonDecoder.decodeJsonElement().jsonObject
        val type = jsonObject["type"]?.jsonPrimitive?.content
            ?: error("Missing type field in HighlightToken.Token")
        val length = jsonObject["length"]?.jsonPrimitive?.int
            ?: error("Missing length field in HighlightToken.Token")
        val content = jsonObject["content"]
            ?: error("Missing content field in HighlightToken.Token")

        return when (content) {
            is JsonArray -> {
                val nestedContent = arrayListOf<HighlightToken.Token>()

                content.forEach { part ->
                    if (part is JsonPrimitive) {
                        nestedContent += StringContent(
                            content = part.content,
                            type = type,
                            length = length,
                        )
                    } else if (part is JsonObject) {
                        nestedContent += format.decodeFromJsonElement(
                            HighlightTokenSerializer,
                            part
                        )
                    } else {
                        error("unknown content part type: $content / $part")
                    }
                }

                HighlightToken.Token.Nested(
                    content = nestedContent,
                    type = type,
                    length = length,
                )
            }

            is JsonPrimitive -> {
                val stringContent = content.content
                StringContent(
                    content = stringContent,
                    type = type,
                    length = length,
                )
            }

            else -> error("Unknown content type: ${content::class.qualifiedName}")
        }
    }
}

object JsObjectSerializer : KSerializer<JsObject> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("JsObject")

    override fun serialize(encoder: Encoder, value: JsObject) {
        val jsonMap = value.mapValues { (_, value) ->
            convertToJsonElement(value)
        }
        encoder.encodeSerializableValue(
            MapSerializer(String.serializer(), JsonElement.serializer()),
            jsonMap
        )
    }

    override fun deserialize(decoder: Decoder): JsObject {
        throw UnsupportedOperationException("JsObject is not supported in common target")
    }

    /**
     * 将任意值转换为JsonElement
     */
    private fun convertToJsonElement(value: Any?): JsonElement {
        return when (value) {
            null -> JsonNull
            is String -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is List<*> -> JsonArray(value.map { convertToJsonElement(it) })
            is Map<*, *> -> {
                val jsonObject = value.entries.associate { (k, v) ->
                    k.toString() to convertToJsonElement(v)
                }
                JsonObject(jsonObject)
            }

            else -> {
                // 对于自定义对象，尝试使用反射序列化
                try {
                    format.encodeToJsonElement(value)
                } catch (_: Exception) {
                    JsonPrimitive(value.toString()) // 降级为字符串
                }
            }
        }
    }
}

