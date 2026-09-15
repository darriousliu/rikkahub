package me.rerere.rikkahub.data.ai.tools.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.koin.mp.KoinPlatform
import kotlin.coroutines.resume

/** Swift owns DeviceActivity's async API. The returned closure cancels its Task with the Kotlin coroutine. */
interface IosScreenTimeProvider {
    val permissionError: String?
    fun requestPermission(completion: (String?) -> Unit): () -> Unit
    fun query(startMillis: Double, endMillis: Double, completion: (String) -> Unit): () -> Unit
}

internal actual val screenTimeAccess: ScreenTimeAccess = object : ScreenTimeAccess {
    private val provider get() = KoinPlatform.getKoin().get<IosScreenTimeProvider>()

    override suspend fun checkPermission() = withContext(Dispatchers.Main) {
        provider.permissionError?.let(::checkError)
        Unit
    }

    override suspend fun requestPermission(): Boolean = withContext(Dispatchers.Main) {
        val error = suspendCancellableCoroutine { continuation ->
            val cancel = provider.requestPermission { result ->
                if (continuation.isActive) continuation.resume(result)
            }
            continuation.invokeOnCancellation { cancel() }
        }
        error?.let(::checkError)
        true
    }

    override suspend fun query(startMs: Long, endMs: Long): List<AppUsage> = withContext(Dispatchers.Main) {
        val response = suspendCancellableCoroutine { continuation ->
            val cancel = provider.query(startMs.toDouble(), endMs.toDouble()) { result ->
                if (continuation.isActive) continuation.resume(result)
            }
            continuation.invokeOnCancellation { cancel() }
        }
        checkError(response)
        Json.parseToJsonElement(response).jsonObject.getValue("apps").jsonArray.map { value ->
            val app = value.jsonObject
            AppUsage(app.getValue("id").jsonPrimitive.content, app.getValue("name").jsonPrimitive.content,
                app.getValue("milliseconds").jsonPrimitive.long)
        }
    }

    private fun checkError(json: String) {
        val result = Json.parseToJsonElement(json).jsonObject
        result["error"]?.jsonPrimitive?.content?.let { code ->
            throw LocalToolAccessException(code, result.getValue("message").jsonPrimitive.content)
        }
    }
}
