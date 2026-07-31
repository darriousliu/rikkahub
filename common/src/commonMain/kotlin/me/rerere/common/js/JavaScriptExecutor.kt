package me.rerere.common.js

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class JavaScriptExecutionRequest(
    val code: String,
    val setupScripts: List<String> = emptyList(),
    val timeoutMillis: Long = 30_000,
)

data class JavaScriptExecution(
    val value: JavaScriptValue,
    val console: List<JavaScriptConsoleMessage>,
)

sealed interface JavaScriptValue {
    data object Null : JavaScriptValue

    data class Scalar(val value: String) : JavaScriptValue

    data class Json(val value: String) : JavaScriptValue
}

fun JavaScriptValue.textOrNull(): String? = when (this) {
    JavaScriptValue.Null -> null
    is JavaScriptValue.Scalar -> value
    is JavaScriptValue.Json -> value
}

enum class JavaScriptConsoleLevel {
    LOG,
    INFO,
    WARN,
    ERROR,
}

data class JavaScriptConsoleMessage(
    val level: JavaScriptConsoleLevel,
    val message: String?,
)

interface JavaScriptExecutor {
    suspend fun execute(request: JavaScriptExecutionRequest): JavaScriptExecution
}

class JavaScriptTimeoutException(
    val timeoutMillis: Long,
) : RuntimeException("JavaScript execution timed out after $timeoutMillis ms")

internal interface JavaScriptRuntimeObserver {
    fun onCreated()

    fun onClosed()
}

internal object NoOpJavaScriptRuntimeObserver : JavaScriptRuntimeObserver {
    override fun onCreated() = Unit

    override fun onClosed() = Unit
}

data class JavaScriptHttpRequest(
    val url: String,
    val method: String,
    val headers: Map<String, String>,
    val body: String?,
)

data class JavaScriptHttpResponse(
    val status: Int,
    val statusText: String,
    val body: String,
)

interface JavaScriptHttpCall {
    fun execute(): JavaScriptHttpResponse

    fun cancel()

    fun close()
}

fun interface JavaScriptHttpTransport {
    fun newCall(request: JavaScriptHttpRequest): JavaScriptHttpCall
}

@Serializable
internal data class JavaScriptFetchResponse(
    val status: Int,
    val ok: Boolean,
    val statusText: String,
    val body: String,
)

internal val javaScriptJson = Json { ignoreUnknownKeys = true }

// fetch() intentionally stays synchronous to preserve existing user scripts.
internal const val SYNCHRONOUS_FETCH_POLYFILL = """
globalThis.fetch = function(url, options) {
    options = options || {};
    var method = (options.method || 'GET').toUpperCase();
    var headers = options.headers ? JSON.stringify(options.headers) : null;
    var body = options.body;
    if (typeof body === 'object' && body !== null) {
        body = JSON.stringify(body);
    } else if (typeof body !== 'string') {
        body = null;
    }

    var raw = __httpRequest(url, method, headers, body);
    var data = JSON.parse(raw);
    return {
        status: data.status,
        ok: data.ok,
        statusText: data.statusText,
        url: url,
        _body: data.body,
        text: function() { return this._body; },
        json: function() { return JSON.parse(this._body); }
    };
};
"""
