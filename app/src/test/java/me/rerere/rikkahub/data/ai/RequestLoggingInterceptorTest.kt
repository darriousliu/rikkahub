package me.rerere.rikkahub.data.ai

import com.sun.net.httpserver.HttpServer
import me.rerere.common.logging.LogEntry
import me.rerere.common.logging.Logging
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSink
import okio.BufferedSource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.net.InetSocketAddress
import java.util.concurrent.CancellationException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.time.Clock
import kotlin.time.TimeSource

class RequestLoggingInterceptorTest {
    @Before
    fun setUp() {
        Logging.clear()
        Logging.setRequestLoggingEnabled(true)
    }

    @After
    fun tearDown() {
        Logging.setRequestLoggingEnabled(false)
        Logging.clear()
    }

    @Test
    fun `disabled logging forwards original objects without serializing or reading bodies`() {
        Logging.setRequestLoggingEnabled(false)
        val body = CountingRequestBody("不应被日志读取")
        val request = request(body)
        val responseBody = UnreadResponseBody("data: 保留流式响应\n\n")
        val response = response(request, responseBody)
        var calls = 0

        val actual = intercept(request) {
            calls++
            assertSame(request, it)
            response
        }

        assertEquals(1, calls)
        assertSame(response, actual)
        assertSame(responseBody, actual.body)
        assertEquals(0, body.writes)
        assertEquals(0, responseBody.sourceAccesses)
        assertTrue(Logging.getRecentLogs().isEmpty())
        actual.use { assertEquals(responseBody.text, it.body.string()) }
    }

    @Test
    fun `successful logging preserves fields and leaves the response body for the caller`() {
        val body = CountingRequestBody("{\"消息\":\"第一行\\n第二行\"}")
        val request = request(body).newBuilder()
            .addHeader("X-Duplicate", "first")
            .addHeader("X-Duplicate", "last")
            .addHeader("X-Trace", "request-trace")
            .build()
        val responseBody = UnreadResponseBody("data: 第一段\n\ndata: [DONE]\n\n")
        val response = response(request, responseBody).newBuilder()
            .addHeader("X-Reply", "first")
            .addHeader("X-Reply", "last")
            .build()
        val before = Clock.System.now().toEpochMilliseconds()

        val actual = intercept(request) {
            assertSame(request, it)
            assertEquals(1, body.writes)
            response
        }

        assertSame(response, actual)
        assertEquals(1, body.writes)
        assertEquals(0, responseBody.sourceAccesses)
        val entry = onlyRequestLog()
        assertEquals("HTTP", entry.tag)
        assertEquals("https://example.test/chat?mode=stream", entry.url)
        assertEquals("POST", entry.method)
        assertEquals(mapOf("X-Duplicate" to "last", "X-Trace" to "request-trace"), entry.requestHeaders)
        assertEquals(body.text, entry.requestBody)
        assertEquals(200, entry.responseCode)
        assertEquals(mapOf("X-Reply" to "last"), entry.responseHeaders)
        assertNull(entry.error)
        assertNotNull(entry.durationMs)
        assertTrue(entry.timestamp in before..Clock.System.now().toEpochMilliseconds())
        actual.use { assertEquals(responseBody.text, it.body.string()) }
    }

    @Test
    fun `http error statuses remain responses with null request bodies`() {
        for (code in listOf(400, 429, 500)) {
            Logging.clear()
            val request = request()
            val body = UnreadResponseBody("服务端错误 $code")
            val response = response(request, body).newBuilder().code(code).build()

            val actual = intercept(request) { response }

            assertSame(response, actual)
            assertEquals(0, body.sourceAccesses)
            val entry = onlyRequestLog()
            assertEquals("GET", entry.method)
            assertNull(entry.requestBody)
            assertEquals(code, entry.responseCode)
            assertNull(entry.error)
            actual.use { assertEquals(body.text, it.body.string()) }
        }
    }

    @Test
    fun `io failures and cancellation are logged once and rethrown unchanged`() {
        for (failure in listOf(IOException("连接中断"), IOException(), CancellationException("请求取消"))) {
            Logging.clear()
            val body = CountingRequestBody("原请求")
            val request = request(body).newBuilder().header("X-Trace", "failure-trace").build()
            var calls = 0

            val actual = assertThrows(Exception::class.java) {
                intercept(request) {
                    calls++
                    assertSame(request, it)
                    throw failure
                }
            }

            assertSame(failure, actual)
            assertEquals(1, calls)
            assertEquals(1, body.writes)
            val entry = onlyRequestLog()
            assertEquals(request.url.toString(), entry.url)
            assertEquals("POST", entry.method)
            assertEquals(mapOf("X-Trace" to "failure-trace"), entry.requestHeaders)
            assertEquals(body.text, entry.requestBody)
            assertEquals(failure.message, entry.error)
            assertNull(entry.responseCode)
            assertTrue(entry.responseHeaders.isEmpty())
            assertNull(entry.durationMs)
        }
    }

    @Test
    fun `errors outside Exception are propagated without creating a request log`() {
        val failure = AssertionError("保持原异常边界")

        assertSame(failure, assertThrows(AssertionError::class.java) {
            intercept(request()) { throw failure }
        })

        assertTrue(Logging.getRecentLogs().isEmpty())
    }

    @Test
    fun `serialization failure before proceed is neither swallowed nor logged`() {
        val failure = IOException("无法序列化请求")
        val body = CountingRequestBody("", failure)
        var calls = 0

        assertSame(failure, assertThrows(IOException::class.java) {
            intercept(request(body)) {
                calls++
                response(it, UnreadResponseBody("不应返回"))
            }
        })

        assertEquals(0, calls)
        assertEquals(1, body.writes)
        assertTrue(Logging.getRecentLogs().isEmpty())
    }

    @Test
    fun `the same interceptor observes the current logging toggle for each request`() {
        val interceptor = RequestLoggingInterceptor()
        for (enabled in listOf(false, true, false, true)) {
            Logging.clear()
            Logging.setRequestLoggingEnabled(enabled)
            val request = request()

            intercept(request, interceptor) { response(it, UnreadResponseBody("返回")) }.close()

            assertEquals(if (enabled) 1 else 0, Logging.getRequestLogs().size)
        }
    }

    @Test
    fun `disabling logging while proceeding suppresses the final success or failure entry`() {
        for (fails in listOf(false, true)) {
            Logging.setRequestLoggingEnabled(true)
            val failure = IOException("关闭后的失败")
            val request = request()
            val proceed: (Request) -> Response = {
                Logging.setRequestLoggingEnabled(false)
                if (fails) throw failure
                response(it, UnreadResponseBody("返回"))
            }

            if (fails) {
                assertSame(failure, assertThrows(IOException::class.java) { intercept(request, proceed = proceed) })
            } else {
                intercept(request, proceed = proceed).close()
            }

            assertTrue(Logging.getRecentLogs().isEmpty())
        }
    }

    @Test
    fun `duration measures actual work in milliseconds`() {
        val startedAt = TimeSource.Monotonic.markNow()

        intercept(request()) {
            Thread.sleep(30)
            response(it, UnreadResponseBody("返回"))
        }.close()

        val elapsedMs = startedAt.elapsedNow().inWholeMilliseconds
        val durationMs = requireNotNull(onlyRequestLog().durationMs)
        assertTrue("durationMs=$durationMs, elapsedMs=$elapsedMs", durationMs in 25..(elapsedMs + 1))
    }

    @Test
    fun `network interceptor preserves a real local http exchange with logging off and on`() {
        val received = LinkedBlockingQueue<List<String>>()
        val responseText = "data: 本地流式样本\n\ndata: [DONE]\n\n"
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/chat") { exchange ->
            exchange.use {
                received.add(listOf(
                    it.requestMethod,
                    it.requestURI.toString(),
                    it.requestHeaders.getFirst("X-Trace"),
                    it.requestBody.readBytes().toString(Charsets.UTF_8),
                ))
                val bytes = responseText.toByteArray(Charsets.UTF_8)
                it.responseHeaders.add("Content-Type", "text/event-stream; charset=utf-8")
                it.responseHeaders.add("X-Reply", "local-server")
                it.sendResponseHeaders(200, bytes.size.toLong())
                it.responseBody.write(bytes)
            }
        }
        server.start()
        val client = OkHttpClient.Builder()
            .addNetworkInterceptor(RequestLoggingInterceptor())
            .callTimeout(5, TimeUnit.SECONDS)
            .build()
        try {
            for (enabled in listOf(false, true)) {
                Logging.clear()
                Logging.setRequestLoggingEnabled(enabled)
                val body = CountingRequestBody("{\"message\":\"本地请求\"}")
                val request = request(body).newBuilder()
                    .url("http://127.0.0.1:${server.address.port}/chat?mode=stream")
                    .header("X-Trace", "local-test")
                    .build()

                client.newCall(request).execute().use {
                    assertEquals(200, it.code)
                    assertEquals("local-server", it.header("X-Reply"))
                    assertEquals(responseText, it.body.string())
                }

                assertEquals(
                    listOf("POST", "/chat?mode=stream", "local-test", body.text),
                    received.poll(5, TimeUnit.SECONDS),
                )
                assertEquals(if (enabled) 2 else 1, body.writes)
                if (enabled) {
                    val entry = onlyRequestLog()
                    assertEquals(request.url.toString(), entry.url)
                    assertEquals(body.text, entry.requestBody)
                    assertEquals("local-test", entry.requestHeaders["X-Trace"])
                    assertEquals(200, entry.responseCode)
                    assertEquals("local-server", entry.responseHeaders.entries.single {
                        it.key.equals("X-Reply", ignoreCase = true)
                    }.value)
                } else {
                    assertTrue(Logging.getRecentLogs().isEmpty())
                }
            }
        } finally {
            server.stop(0)
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }

    private fun request(body: RequestBody? = null): Request = Request.Builder()
        .url("https://example.test/chat?mode=stream")
        .apply { if (body != null) post(body) }
        .build()

    private fun response(request: Request, body: ResponseBody): Response = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .body(body)
        .build()

    private fun intercept(
        request: Request,
        interceptor: RequestLoggingInterceptor = RequestLoggingInterceptor(),
        proceed: (Request) -> Response,
    ): Response {
        val client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .addInterceptor { chain -> proceed(chain.request()) }
            .build()
        return try {
            client.newCall(request).execute()
        } finally {
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }

    private fun onlyRequestLog(): LogEntry.RequestLog = Logging.getRequestLogs().single().also {
        assertSame(it, Logging.getRecentLogs().single())
    }

    private class CountingRequestBody(val text: String, private val failure: IOException? = null) : RequestBody() {
        var writes = 0
            private set

        override fun contentType() = "application/json; charset=utf-8".toMediaType()

        override fun writeTo(sink: BufferedSink) {
            writes++
            failure?.let { throw it }
            sink.writeUtf8(text)
        }
    }

    private class UnreadResponseBody(val text: String) : ResponseBody() {
        private val buffer = Buffer().writeUtf8(text)
        var sourceAccesses = 0
            private set

        override fun contentType() = "text/event-stream; charset=utf-8".toMediaType()

        override fun contentLength() = text.toByteArray(Charsets.UTF_8).size.toLong()

        override fun source(): BufferedSource {
            sourceAccesses++
            return buffer
        }
    }
}
