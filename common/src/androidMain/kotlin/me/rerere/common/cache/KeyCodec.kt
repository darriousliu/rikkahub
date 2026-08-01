package me.rerere.common.cache

import kotlinx.serialization.KSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64

interface KeyCodec<K : Any> {
    fun toFileName(key: K): String
    fun fromFileName(name: String): K?
}

class Base64JsonKeyCodec<K : Any>(
    private val keySerializer: KSerializer<K>,
    private val json: Json = Json { allowStructuredMapKeys = true }
) : KeyCodec<K> {
    private val base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

    override fun toFileName(key: K): String {
        val jsonStr = json.encodeToString(keySerializer, key)
        return base64.encode(jsonStr.encodeToByteArray())
    }

    override fun fromFileName(name: String): K? = try {
        val jsonStr = base64.decode(name).decodeToString()
        json.decodeFromString(keySerializer, jsonStr)
    } catch (_: Exception) {
        null
    }
}
