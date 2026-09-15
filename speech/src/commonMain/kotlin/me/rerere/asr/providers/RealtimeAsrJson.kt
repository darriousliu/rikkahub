package me.rerere.asr.providers

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Keep Android JSONObject.optString's missing-value and string-coercion behavior.
internal fun JsonObject.optString(name: String, fallback: String = ""): String =
    when (val value = this[name]) {
        null -> fallback
        is JsonPrimitive -> value.content
        else -> value.toString()
    }
