package me.rerere.rikkahub.data.ai.mcp

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.platform.OAuthCallback
import me.rerere.rikkahub.platform.OAuthCallbackSession
import me.rerere.rikkahub.platform.OAuthCallbackSessionFactory
import me.rerere.rikkahub.utils.JsonInstant
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** Exercises the real SDK and SettingsStore without changing production code to make it testable. */
class McpManagerContractTest {
    private val fixtures = mutableListOf<Fixture>()

    @AfterTest
    fun tearDown() = runBlocking {
        fixtures.forEach { fixture ->
            fixture.store.settingsFlow.value.mcpServers.forEach { fixture.manager.removeClient(it) }
            fixture.scope.cancel()
            fixture.client.close()
            fixture.engine.close()
        }
    }

    @Test
    fun `connect sync and disable preserve status client visibility and saved tool flags`() = runBlocking {
        val fixture = fixture()
        val initial = fixture.current()
        assertEquals(McpStatus.Idle, fixture.runtime.getStatus(initial).first())
        assertFalse(fixture.connected(initial))

        fixture.enable()
        assertTrue(fixture.connected(initial))
        assertEquals(McpStatus.Connected, fixture.runtime.syncingStatus.value[initial.id])
        assertEquals(listOf("echo", "image"), fixture.current().commonOptions.tools.map { it.name })
        assertFalse(fixture.current().commonOptions.tools.first().enable)
        assertTrue(fixture.current().commonOptions.tools.first().needsApproval)
        assertTrue(fixture.current().commonOptions.tools.last().enable)
        assertFalse(fixture.current().commonOptions.tools.last().needsApproval)

        fixture.description = "updated description"
        fixture.runtime.syncAll()
        assertEquals("updated description", fixture.current().commonOptions.tools.first().description)
        assertFalse(fixture.current().commonOptions.tools.first().enable)
        assertTrue(fixture.current().commonOptions.tools.first().needsApproval)
        assertNotNull(fixture.current().commonOptions.tools.first().inputSchema)
        assertEquals(listOf(fixture.current()), fixture.persisted())
        assertEquals(1, fixture.rpc.count { it.message["method"]?.jsonPrimitive?.content == "initialize" })
        assertEquals(2, fixture.rpc.count { it.message["method"]?.jsonPrimitive?.content == "tools/list" })

        fixture.setEnabled(false)
        fixture.awaitStatus(McpStatus.Idle)
        assertFalse(fixture.connected(initial))
        assertFalse(initial.id in fixture.runtime.syncingStatus.value)
    }

    @Test
    fun `available tools follow the current assistant server and tool switches in stored order`() = runBlocking {
        val first = server()
        val second = server().copy(commonOptions = McpCommonOptions(enable = false, name = "second"))
        val fixture = fixture(listOf(first, second))
        fixture.enable(first.id)
        fixture.enable(second.id)
        assertEquals(
            listOf(Triple(first.id, "fixture", "image"), Triple(second.id, "second", "echo"),
                Triple(second.id, "second", "image")),
            fixture.runtime.getAllAvailableTools().map { Triple(it.first, it.second, it.third.name) },
        )

        val other = Assistant(name = "other", mcpServers = setOf(second.id))
        fixture.store.update { it.copy(assistantId = other.id, assistants = it.assistants + other) }
        fixture.store.settingsFlow.first { it.assistantId == other.id }
        assertEquals(listOf("echo", "image"), fixture.runtime.getAllAvailableTools().map { it.third.name })
        fixture.setEnabled(false, second.id)
        assertTrue(fixture.runtime.getAllAvailableTools().isEmpty())
    }

    @Test
    fun `SDK tool parameters headers text image and embedded resource mapping remain intact`() = runBlocking {
        val fixture = fixture()
        fixture.enable()
        val args = json("""{"value":"hello","nested":{"count":2},"items":[true,null]}""")
        val result = fixture.runtime.callTool(fixture.current().id, "echo", args)

        assertEquals(UIMessagePart.Text("fixed text"), result[0])
        assertEquals(UIMessagePart.Image(url = "file:///fixture.png"), result[1])
        assertContentEquals(byteArrayOf(1, 2, 3), fixture.images.single().first)
        assertEquals("image/png", fixture.images.single().second)
        assertEquals(
            json("""{"type":"resource","resource":{"uri":"fixture://document","mimeType":"text/plain","text":"embedded content","_meta":null},"annotations":null,"_meta":null}"""),
            json(assertIs<UIMessagePart.Text>(result[2]).text),
        )
        val request = fixture.rpc.single { it.message["method"]?.jsonPrimitive?.content == "tools/call" }
        assertEquals("echo", request.message["params"]?.jsonObject?.get("name")?.jsonPrimitive?.content)
        assertEquals(args, request.message["params"]?.jsonObject?.get("arguments"))
        assertEquals("fixed-header", request.request.headers["X-Cmp-Contract"])
        assertEquals(HttpMethod.Post, request.request.method)
        assertEquals("/mcp", request.request.url.encodedPath)
    }

    @Test
    fun `tool error content is retained and JSON RPC errors propagate`() = runBlocking {
        val fixture = fixture()
        fixture.enable()
        fixture.toolResult = """{"content":[{"type":"text","text":"original tool failure"}],"isError":true}"""
        assertEquals(
            listOf(UIMessagePart.Text("original tool failure")),
            fixture.runtime.callTool(fixture.current().id, "echo", json("{}")),
        )
        fixture.rpcError = true
        val failure = runCatching { fixture.runtime.callTool(fixture.current().id, "echo", json("{}")) }
        assertTrue(failure.isFailure)
        assertTrue(failure.exceptionOrNull()?.message.orEmpty().contains("fixed RPC failure"))
    }

    @Test
    fun `cancelling an in flight call preserves cancellation instead of returning error text`() = runBlocking {
        val fixture = fixture()
        fixture.enable()
        fixture.holdToolCall = true
        val call = async { fixture.runtime.callTool(fixture.current().id, "echo", json("{}")) }
        withTimeout(10_000) { fixture.toolEntered.await() }
        withTimeout(10_000) { call.cancelAndJoin() }
        assertTrue(call.isCancelled)
        assertTrue(runCatching { call.await() }.exceptionOrNull() is kotlinx.coroutines.CancellationException)
    }

    @Test
    fun `missing client returns the original text without a network request`() = runBlocking {
        val fixture = fixture()
        val missingId = Uuid.random()
        assertEquals(
            listOf(UIMessagePart.Text("Failed to execute MCP tool: No MCP session for server $missingId")),
            fixture.runtime.callTool(missingId, "echo", json("{}")),
        )
        assertTrue(fixture.rpc.isEmpty())
        assertTrue(fixture.requests.isEmpty())
    }

    @Test
    fun `manager authorization and cancellation publish the same status and close the callback session`() = runBlocking {
        val configured = server().let {
            it.copy(commonOptions = it.commonOptions.copy(oauth = McpOAuthState(enabled = true, clientId = "fixture")))
        }
        val fixture = fixture(listOf(configured))
        fixture.runtime.startAuthorization(configured)
        withTimeout(10_000) { fixture.authorizationEntered.await() }
        assertEquals(McpStatus.Authorizing, fixture.runtime.getStatus(configured).first())
        assertEquals(McpStatus.Authorizing, fixture.runtime.syncingStatus.value[configured.id])

        fixture.runtime.cancelAuthorization(configured)
        withTimeout(10_000) { fixture.callbackClosed.await() }
        fixture.awaitStatus(McpStatus.NeedsAuthorization)
        assertEquals(1, fixture.callbackCreations)
        assertTrue(fixture.requests.none { it.url.encodedPath == "/token" })
        assertEquals("https://auth.example.test/authorize", fixture.current().commonOptions.oauth?.authorizationEndpoint)
    }

    private fun fixture(servers: List<McpServerConfig> = listOf(server())) = Fixture(servers).also(fixtures::add)

    private class Fixture(servers: List<McpServerConfig>) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        private val assistant = Assistant(name = "fixture", mcpServers = servers.map { it.id }.toSet())
        private val preferences = MemoryPreferences(servers, assistant)
        val store = SettingsStore(preferences, scope)
        val requests = ConcurrentLinkedQueue<HttpRequestData>()
        val rpc = ConcurrentLinkedQueue<RpcRequest>()
        val images = ConcurrentLinkedQueue<Pair<ByteArray, String>>()
        @Volatile var description = "initial description"
        @Volatile var toolResult = """{"content":[{"type":"text","text":"fixed text"},{"type":"image","data":"AQID","mimeType":"image/png"},$EMBEDDED_RESOURCE]}"""
        @Volatile var rpcError = false
        @Volatile var holdToolCall = false
        val toolEntered = CompletableDeferred<Unit>()
        val authorizationEntered = CompletableDeferred<Unit>()
        val callbackClosed = CompletableDeferred<Unit>()
        var callbackCreations = 0
        val engine = MockEngine { request ->
            requests += request
            val headers = headersOf(HttpHeaders.ContentType, "application/json")
            when {
                request.url.encodedPath == "/.well-known/oauth-protected-resource/mcp" -> respond(
                    """{"authorization_servers":["https://auth.example.test"]}""", headers = headers,
                )
                request.url.encodedPath == "/.well-known/oauth-authorization-server" -> respond(
                    """{"authorization_endpoint":"https://auth.example.test/authorize","token_endpoint":"https://auth.example.test/token"}""",
                    headers = headers,
                )
                request.method != HttpMethod.Post -> respond("", HttpStatusCode.MethodNotAllowed)
                else -> {
                    val message = json(request.body.toByteArray().decodeToString())
                    rpc += RpcRequest(request, message)
                    val id = message["id"]
                    if (id == null) {
                        respond("", HttpStatusCode.Accepted)
                    } else {
                        val method = message["method"]?.jsonPrimitive?.content
                        val result = when (method) {
                            "initialize" -> """{"protocolVersion":${message["params"]?.jsonObject?.get("protocolVersion")},"capabilities":{"tools":{}},"serverInfo":{"name":"fixture","version":"1.0"}}"""
                            "tools/list" -> """{"tools":[{"name":"echo","description":"$description","inputSchema":{"type":"object","properties":{"value":{"type":"string"}},"required":["value"]}},{"name":"image","inputSchema":{"type":"object","properties":{}}}]}"""
                            "tools/call" -> {
                                toolEntered.complete(Unit)
                                if (holdToolCall) awaitCancellation()
                                toolResult
                            }
                            else -> error("Unexpected MCP method: $method")
                        }
                        val response = if (method == "tools/call" && rpcError) {
                            """{"jsonrpc":"2.0","id":$id,"error":{"code":-32603,"message":"fixed RPC failure"}}"""
                        } else {
                            """{"jsonrpc":"2.0","id":$id,"result":$result}"""
                        }
                        respond(response, headers = headers)
                    }
                }
            }
        }
        val client = HttpClient(engine) { install(SSE) }
        val manager = McpManager(
            settingsStore = store,
            appScope = scope,
            imageStore = McpImageStore { bytes, mime ->
                images += bytes to mime
                UIMessagePart.Image(url = "file:///fixture.png")
            },
            callbackSessionFactory = OAuthCallbackSessionFactory {
                callbackCreations++
                object : OAuthCallbackSession {
                    override val redirectUri = "dev.rikkahub.test:/oauth/callback"
                    override suspend fun authorize(authorizationUri: String, expectedState: String): OAuthCallback {
                        authorizationEntered.complete(Unit)
                        awaitCancellation()
                    }
                    override suspend fun close() { callbackClosed.complete(Unit) }
                }
            },
            httpClient = client,
        )
        val runtime: McpManager = manager

        fun connected(config: McpServerConfig): Boolean = runtime.getClient(config) != null
        fun current(id: Uuid = assistant.mcpServers.first()): McpServerConfig =
            store.settingsFlow.value.mcpServers.first { it.id == id }

        suspend fun enable(id: Uuid = current().id) {
            setEnabled(true, id)
            awaitStatus(McpStatus.Connected, id)
        }

        suspend fun setEnabled(enabled: Boolean, id: Uuid = current().id) {
            store.update { settings ->
                settings.copy(mcpServers = settings.mcpServers.map {
                    if (it.id == id) it.clone(commonOptions = it.commonOptions.copy(enable = enabled)) else it
                })
            }
            store.settingsFlow.first { settings -> settings.mcpServers.first { it.id == id }.commonOptions.enable == enabled }
        }

        suspend fun awaitStatus(status: McpStatus, id: Uuid = current().id) = withTimeout(10_000) {
            runtime.getStatus(current(id)).first { it == status }
        }

        fun persisted(): List<McpServerConfig> = JsonInstant.decodeFromString(
            assertNotNull(preferences.data.value[SettingsStore.MCP_SERVERS]),
        )
    }

    private data class RpcRequest(val request: HttpRequestData, val message: JsonObject)

    private class MemoryPreferences(servers: List<McpServerConfig>, assistant: Assistant) : DataStore<Preferences> {
        private val mutex = Mutex()
        override val data = MutableStateFlow(preferencesOf(
            SettingsStore.MCP_SERVERS to JsonInstant.encodeToString(servers),
            SettingsStore.ASSISTANTS to JsonInstant.encodeToString(listOf(assistant)),
            SettingsStore.SELECT_ASSISTANT to assistant.id.toString(),
        ))

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = mutex.withLock {
            transform(data.value).also { data.value = it }
        }
    }

    private companion object {
        const val EMBEDDED_RESOURCE = """{"type":"resource","resource":{"uri":"fixture://document","mimeType":"text/plain","text":"embedded content"}}"""
        fun json(value: String): JsonObject = JsonInstant.parseToJsonElement(value).jsonObject
        fun server() = McpServerConfig.StreamableHTTPServer(
            id = Uuid.random(),
            commonOptions = McpCommonOptions(
                enable = false,
                name = "fixture",
                headers = listOf("X-Cmp-Contract" to "fixed-header"),
                tools = listOf(McpTool(name = "echo", enable = false, needsApproval = true), McpTool(name = "removed")),
            ),
            url = "https://mcp.example.test/mcp",
        )
    }
}
