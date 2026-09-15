package me.rerere.rikkahub.data.ai.tools.local

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal data class AppUsage(val appId: String, val appName: String, val totalMillis: Long)

internal interface ScreenTimeAccess {
    suspend fun checkPermission()
    suspend fun requestPermission(): Boolean
    suspend fun query(startMs: Long, endMs: Long): List<AppUsage>
}

internal expect val screenTimeAccess: ScreenTimeAccess

internal class LocalToolAccessException(val code: String, override val message: String) : Exception(message) {
    fun payload() = buildJsonObject {
        put("error", code)
        put("message", message)
    }
}
