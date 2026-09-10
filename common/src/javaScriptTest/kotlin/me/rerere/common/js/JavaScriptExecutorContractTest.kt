package me.rerere.common.js

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Runs the actual QuickJS runtime and Ktor request pipeline on each supported test target. */
class JavaScriptExecutorContractTest {
    private val fixtures = mutableListOf<Fixture>()

    @AfterTest
    fun closeClients() {
        fixtures.forEach {
            it.client.close()
            it.engine.close()
        }
    }

    @Test
    fun resultKindsSetupOrderAndRuntimeIsolation() = runBlocking {
        val executor = DefaultJavaScriptExecutor()
        val cases = listOf(
            "null" to JavaScriptValue.Null,
            "undefined" to JavaScriptValue.Null,
            "false" to JavaScriptValue.Scalar("false"),
            "1 + 2" to JavaScriptValue.Scalar("3"),
            "'中文\\nvalue'" to JavaScriptValue.Scalar("中文\nvalue"),
            "({a: 1, b: [true, null]})" to JavaScriptValue.Json("""{"a":1,"b":[true,null]}"""),
            "(function() {})" to JavaScriptValue.Null,
        )
        for ((code, expected) in cases) {
            assertEquals(expected, executor.execute(JavaScriptExecutionRequest(code)).value, code)
        }
        assertEquals(
            JavaScriptValue.Scalar("first-second"),
            executor.execute(
                JavaScriptExecutionRequest(
                    code = "order",
                    setupScripts = listOf("var order = 'first';", "order += '-second';"),
                ),
            ).value,
        )
        assertEquals(
            JavaScriptValue.Scalar("undefined"),
            executor.execute(JavaScriptExecutionRequest("typeof order")).value,
        )
    }

    @Test
    fun consoleFormattingAndSavedBuiltins() = runBlocking {
        val execution = DefaultJavaScriptExecutor().execute(
            JavaScriptExecutionRequest(
                code = """
                    console.log('a', 2, {x: 3}, null, undefined);
                    console.debug('debug');
                    console.info(true);
                    console.warn('warn');
                    console.error('error');
                    globalThis.JSON = {stringify: function() { return 'changed'; }};
                    ({still: 'original'})
                """.trimIndent(),
            ),
        )
        assertEquals(
            listOf(
                JavaScriptConsoleMessage(JavaScriptConsoleLevel.LOG, "'a', 2, {\"x\":3}, null, undefined"),
                JavaScriptConsoleMessage(JavaScriptConsoleLevel.LOG, "'debug'"),
                JavaScriptConsoleMessage(JavaScriptConsoleLevel.INFO, "true"),
                JavaScriptConsoleMessage(JavaScriptConsoleLevel.WARN, "'warn'"),
                JavaScriptConsoleMessage(JavaScriptConsoleLevel.ERROR, "'error'"),
            ),
            execution.console,
        )
        assertEquals(JavaScriptValue.Json("""{"still":"original"}"""), execution.value)
    }

    @Test
    fun scriptAndSetupFailuresDoNotPoisonLaterExecution() = runBlocking {
        val executor = DefaultJavaScriptExecutor()
        val error = assertFails {
            executor.execute(JavaScriptExecutionRequest("throw new Error('cmp12-script-error')"))
        }
        assertTrue(error.message.orEmpty().contains("cmp12-script-error"))
        assertFails {
            executor.execute(JavaScriptExecutionRequest("1", setupScripts = listOf("const = ;")))
        }
        assertEquals(JavaScriptValue.Scalar("7"), executor.execute(JavaScriptExecutionRequest("7")).value)
    }

    @Test
    fun busyScriptTimeoutRetainsTimeoutTypeAndRuntimeRemainsUsable() = runBlocking {
        val executor = DefaultJavaScriptExecutor()
        val error = withTimeout(10_000) {
            assertFailsWith<JavaScriptTimeoutException> {
                executor.execute(JavaScriptExecutionRequest("while (true) {}", timeoutMillis = 500))
            }
        }
        assertEquals(500, error.timeoutMillis)
        assertEquals(JavaScriptValue.Scalar("9"), executor.execute(JavaScriptExecutionRequest("3 * 3")).value)
    }

    @Test
    fun disabledTimeoutAndAbsentFetchRemainSupported() = runBlocking {
        val executor = DefaultJavaScriptExecutor()
        for (timeout in listOf(0L, -1L)) {
            assertEquals(
                JavaScriptValue.Scalar("undefined:42"),
                executor.execute(
                    JavaScriptExecutionRequest("typeof fetch + ':' + (6 * 7)", timeoutMillis = timeout),
                ).value,
            )
        }
    }

    @Test
    fun synchronousFetchPreservesResponseAndObjectRequestBody() = runBlocking {
        val fixture = fixture {
            respond(
                """{"message":"中文","n":3}""",
                HttpStatusCode(207, "Multi-Status"),
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val execution = fixture.run(
            """
                var response = fetch('https://fixture.invalid/echo?x=1', {
                    method: 'pOsT',
                    headers: {'X-CMP12': 'yes'},
                    body: {message: '中文', n: 3}
                });
                ({
                    status: response.status, ok: response.ok, statusText: response.statusText,
                    url: response.url, text: response.text(), json: response.json(),
                    promise: typeof response.then
                })
            """.trimIndent(),
        )
        val request = fixture.requests.receive()
        assertEquals("POST", request.method.value)
        assertEquals("https://fixture.invalid/echo?x=1", request.url.toString())
        assertEquals("yes", request.headers["X-CMP12"])
        assertEquals("""{"message":"中文","n":3}""", request.body.toByteArray().decodeToString())
        assertEquals("application/json; charset=utf-8", request.body.contentType.toString())
        val result = Json.parseToJsonElement(assertIs<JavaScriptValue.Json>(execution.value).value).jsonObject
        assertEquals("207", result["status"]?.jsonPrimitive?.content)
        assertEquals("true", result["ok"]?.jsonPrimitive?.content)
        assertEquals("Multi-Status", result["statusText"]?.jsonPrimitive?.content)
        assertEquals("https://fixture.invalid/echo?x=1", result["url"]?.jsonPrimitive?.content)
        assertEquals("""{"message":"中文","n":3}""", result["text"]?.jsonPrimitive?.content)
        assertEquals(Json.parseToJsonElement("""{"message":"中文","n":3}"""), result["json"])
        assertEquals("undefined", result["promise"]?.jsonPrimitive?.content)
    }

    @Test
    fun methodAndBodyRulesStayCompatible() = runBlocking {
        val fixture = fixture()
        data class Case(val method: String, val bodyScript: String, val expected: String, val hasBody: Boolean)
        val cases = listOf(
            Case("get", "'ignored'", "", false),
            Case("HEAD", "'ignored'", "", false),
            Case("POST", "undefined", "", true),
            Case("PUT", "42", "", true),
            Case("PATCH", "null", "", true),
            Case("DELETE", "null", "", false),
            Case("DELETE", "'delete-body'", "delete-body", true),
            Case("OPTIONS", "'options-body'", "options-body", true),
        )
        for (case in cases) {
            fixture.run(
                "fetch('https://fixture.invalid/method', {method: " + JsonPrimitive(case.method) +
                    ", body: " + case.bodyScript + "}).text()",
            )
            val request = fixture.requests.receive()
            assertEquals(case.method.uppercase(), request.method.value)
            assertEquals(case.expected, request.body.toByteArray().decodeToString(), case.method)
            assertEquals(case.hasBody, request.body.contentType != null, case.method)
        }
    }

    @Test
    fun explicitContentTypeAndPrimitiveHeadersArePreserved() = runBlocking {
        val fixture = fixture()
        fixture.run(
            """
                fetch('https://fixture.invalid/headers', {
                    method: 'POST',
                    headers: {'content-type': 'text/plain; charset=us-ascii', 'X-Number': 7},
                    body: 'plain'
                }).text()
            """.trimIndent(),
        )
        val request = fixture.requests.receive()
        assertEquals("text/plain; charset=us-ascii", request.body.contentType.toString())
        assertEquals("7", request.headers["X-Number"])
        assertEquals("plain", request.body.toByteArray().decodeToString())
    }

    @Test
    fun unsuccessfulAndEmptyResponsesRemainReadable() = runBlocking {
        val fixture = fixture { request ->
            if (request.url.encodedPath == "/empty") respond("", HttpStatusCode.NoContent)
            else respond("missing", HttpStatusCode.NotFound)
        }
        assertEquals(
            JavaScriptValue.Json("""{"status":404,"ok":false,"body":"missing"}"""),
            fixture.run(
                "var r = fetch('https://fixture.invalid/missing'); ({status:r.status,ok:r.ok,body:r.text()})",
            ).value,
        )
        assertEquals(JavaScriptValue.Scalar(""), fixture.run("fetch('https://fixture.invalid/empty').text()").value)
    }

    @Test
    fun responseParsingAndTransportErrorsAreNotSwallowed() = runBlocking {
        val invalidJson = fixture { respond("not json") }
        assertFails { invalidJson.run("fetch('https://fixture.invalid/json').json()") }
        val broken = fixture { throw IllegalStateException("cmp12-http-error") }
        val error = assertFails { broken.run("fetch('https://fixture.invalid/error')") }
        assertTrue(error.message.orEmpty().contains("cmp12-http-error"))
    }

    @Test
    fun timeoutCancelsAnActiveHttpRequest() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val fixture = fixture {
            entered.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                cancelled.complete(Unit)
            }
        }
        val error = withTimeout(10_000) {
            assertFailsWith<JavaScriptTimeoutException> {
                fixture.run("fetch('https://fixture.invalid/wait')", timeoutMillis = 1_000)
            }
        }
        assertEquals(1_000, error.timeoutMillis)
        assertTrue(entered.isCompleted)
        withTimeout(5_000) { cancelled.await() }
    }

    @Test
    fun callerCancellationStopsFetchAndKeepsSharedClientUsable() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val fixture = fixture { request ->
            if (request.url.encodedPath == "/wait") {
                entered.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    cancelled.complete(Unit)
                }
            } else {
                respond("still-open")
            }
        }
        val call = async(Dispatchers.Default) { fixture.run("fetch('https://fixture.invalid/wait')") }
        withTimeout(5_000) { entered.await() }
        call.cancel()
        withTimeout(5_000) {
            cancelled.await()
            assertFailsWith<CancellationException> { call.await() }
        }
        assertEquals(
            JavaScriptValue.Scalar("still-open"),
            fixture.run("fetch('https://fixture.invalid/next').text()").value,
        )
    }

    @Test
    fun concurrentExecutionsDoNotShareCancellationState() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val fixture = fixture { request ->
            if (request.url.encodedPath == "/slow") {
                entered.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    cancelled.complete(Unit)
                }
            } else {
                respond("fast")
            }
        }
        val slow = async(Dispatchers.Default) { fixture.run("fetch('https://fixture.invalid/slow')") }
        try {
            withTimeout(5_000) { entered.await() }
            assertEquals(
                JavaScriptValue.Scalar("fast"),
                withTimeout(5_000) { fixture.run("fetch('https://fixture.invalid/fast').text()") }.value,
            )
            assertFalse(cancelled.isCompleted)
        } finally {
            withTimeout(5_000) { slow.cancelAndJoin() }
        }
        withTimeout(5_000) { cancelled.await() }
    }

    private fun fixture(handler: MockRequestHandler = { respond("ok") }): Fixture =
        Fixture(handler).also(fixtures::add)

    private class Fixture(handler: MockRequestHandler) {
        val requests = Channel<HttpRequestData>(Channel.UNLIMITED)
        val engine = MockEngine { request ->
            requests.send(request)
            handler(request)
        }
        val client = HttpClient(engine)
        private val executor = DefaultJavaScriptExecutor(client)

        suspend fun run(code: String, timeoutMillis: Long = 5_000): JavaScriptExecution =
            executor.execute(JavaScriptExecutionRequest(code = code, timeoutMillis = timeoutMillis))
    }
}
