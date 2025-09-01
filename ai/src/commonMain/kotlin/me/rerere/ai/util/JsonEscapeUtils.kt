package me.rerere.ai.util

import kotlinx.serialization.json.Json

fun String.unescapeJson(): String {
    return try {
        Json.decodeFromString("\"$this\"")
    } catch (_: Exception) {
        this // 如果解析失败，返回原字符串
    }
}

fun String.escapeJson(): String {
    return Json.encodeToString(this).removeSurrounding("\"")
}
