package me.rerere.common.js

import com.whl.quickjs.wrapper.JSCallFunction
import com.whl.quickjs.wrapper.QuickJSContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal fun QuickJSContext.injectFetch(
    transport: JavaScriptHttpTransport,
    executionState: JavaScriptExecutionState,
    fetchDispatcher: CoroutineDispatcher,
) {
    globalObject.setProperty("__httpRequest", JSCallFunction { args ->
        val url = args[0] as? String ?: error("url is required")
        val method = (args[1] as? String ?: "GET").uppercase()
        val headersJson = args[2] as? String
        val body = args[3] as? String
        val headers = if (!headersJson.isNullOrBlank() && headersJson != "null") {
            javaScriptJson.parseToJsonElement(headersJson).jsonObject
                .mapValues { (_, value) -> value.jsonPrimitive.content }
        } else {
            emptyMap()
        }

        val call = transport.newCall(
            JavaScriptHttpRequest(
                url = url,
                method = method,
                headers = headers,
                body = body,
            ),
        )
        try {
            executionState.install(call)
            val response = runBlocking(fetchDispatcher) { call.execute() }
            javaScriptJson.encodeToString(
                JavaScriptFetchResponse(
                    status = response.status,
                    ok = response.status in 200..299,
                    statusText = response.statusText,
                    body = response.body,
                ),
            )
        } finally {
            executionState.clear(call)
            call.close()
        }
    })

    evaluate(SYNCHRONOUS_FETCH_POLYFILL)
}
