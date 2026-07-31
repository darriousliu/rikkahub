package me.rerere.common.js

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.function
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal suspend fun QuickJs.installFetch(
    transport: JavaScriptHttpTransport,
    executionState: JavaScriptExecutionState,
    fetchDispatcher: CoroutineDispatcher,
) {
    function<String>("__httpRequest") { args ->
        val url = args.getOrNull(0) as? String ?: error("url is required")
        val method = (args.getOrNull(1) as? String ?: "GET").uppercase()
        val headersJson = args.getOrNull(2) as? String
        val body = args.getOrNull(3) as? String
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
    }

    evaluate<Any?>(SYNCHRONOUS_FETCH_POLYFILL + "\n;void 0;", "fetch-polyfill.js")
}
