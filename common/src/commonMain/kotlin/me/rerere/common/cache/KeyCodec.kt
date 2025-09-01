package me.rerere.common.cache

import io.ktor.utils.io.charsets.Charsets
import io.ktor.utils.io.core.toByteArray
import kotlinx.serialization.KSerializer
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
    val base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
    override fun toFileName(key: K): String {
        val jsonStr = json.encodeToString(keySerializer, key)
        val bytes = jsonStr.toByteArray(Charsets.UTF_8)
        return base64.encode(bytes)
    }

    override fun fromFileName(name: String): K? = try {
        val decoded = base64.decode(name)
        val jsonStr = decoded.decodeToString()
        json.decodeFromString(keySerializer, jsonStr)
    } catch (_: Exception) {
        null
    }
}

