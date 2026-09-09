package me.rerere.rikkahub.data.ai.mcp

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import io.ktor.http.parseQueryString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import me.rerere.common.crypto.PlatformSha256Crypto
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.platform.OAuthCallback
import me.rerere.rikkahub.platform.OAuthCallbackSession
import me.rerere.rikkahub.platform.OAuthCallbackSessionFactory
import me.rerere.rikkahub.utils.JsonInstant
import kotlin.io.encoding.Base64
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class McpOAuthCoordinatorTest {
    private val fixtures = mutableListOf<Fixture>()

    @AfterTest
    fun tearDown() {
        fixtures.forEach {
            it.scope.cancel()
            it.client.close()
            it.engine.close()
        }
        fixtures.clear()
    }

    @Test
    fun `ineligible and incomplete configurations do not request or persist`() = runTest {
        val base = oauth()
        val cases = listOf(
            null,
            base.copy(enabled = false, accessToken = null),
            base.copy(refreshToken = null),
            base.copy(refreshToken = ""),
            base.copy(refreshToken = "  "),
            base.copy(expiresAt = NOW + 60_001),
            base.copy(expiresAt = 0),
            base.copy(expiresAt = -1),
            base.copy(tokenEndpoint = null),
            base.copy(clientId = null),
        )
        for ((index, state) in cases.withIndex()) {
            val fixture = fixture(server(state))
            val current = fixture.current()
            val before = fixture.preferences.data.value

            assertSame(current, fixture.coordinator.ensureFreshToken(current), "case $index")
            assertTrue(fixture.requests.isEmpty(), "case $index")
            assertTrue(fixture.preferences.writes.isEmpty(), "case $index")
            assertEquals(before, fixture.preferences.data.value, "case $index")
        }
    }

    @Test
    fun `refresh starts exactly at sixty seconds or when access token is blank`() = runTest {
        val cases = listOf(
            oauth(expiresAt = NOW + 60_000),
            oauth(expiresAt = NOW + 59_999),
            oauth(expiresAt = NOW),
            oauth(expiresAt = NOW - 1),
            oauth().copy(accessToken = null, expiresAt = NOW + 120_000),
            oauth().copy(accessToken = "", expiresAt = 0),
            oauth().copy(accessToken = "  ", expiresAt = -1),
        )
        for (state in cases) {
            val fixture = fixture(server(state))
            val refreshed = fixture.coordinator.ensureFreshToken(fixture.current())

            assertEquals(1, fixture.requests.size)
            assertEquals("new-access", refreshed.commonOptions.oauth?.accessToken)
            assertEquals(NOW + 120_000, refreshed.commonOptions.oauth?.expiresAt)
            assertEquals(listOf(refreshed), fixture.persisted())
        }
    }

    @Test
    fun `the same coordinator observes clock changes at the refresh boundary`() = runTest {
        val fixture = fixture(server(oauth(expiresAt = NOW + 60_001)))
        val input = fixture.current()
        assertSame(input, fixture.coordinator.ensureFreshToken(input))
        assertTrue(fixture.requests.isEmpty())

        fixture.clock.milliseconds++
        val refreshed = fixture.coordinator.ensureFreshToken(input)
        assertEquals(1, fixture.requests.size)
        assertEquals(NOW + 120_001, refreshed.commonOptions.oauth?.expiresAt)

        assertEquals(refreshed, fixture.coordinator.ensureFreshToken(input))
        assertEquals(1, fixture.requests.size)
    }

    @Test
    fun `refresh preserves form fields transport metadata and unrelated persisted servers`() = runTest {
        val http = server(oauth())
        val sse = McpServerConfig.SseTransportServer(commonOptions = http.commonOptions, url = http.url)
        for (input in listOf(http, sse)) {
            val unrelated = server(oauth().copy(accessToken = "unrelated-access"))
            val fixture = fixture(input, unrelated)
            fixture.tokenResponse = """
                {"access_token":"new-access","refresh_token":"rotated-refresh",
                 "expires_in":120,"scope":"tools:new"}
            """.trimIndent()

            val refreshed = fixture.coordinator.ensureFreshToken(input)
            val request = fixture.requests.single()
            assertEquals(HttpMethod.Post, request.method)
            assertEquals(TOKEN_ENDPOINT, request.url.toString())
            assertEquals("application/x-www-form-urlencoded; charset=UTF-8", request.body.contentType.toString())
            assertEquals("application/json", request.headers[HttpHeaders.Accept])
            assertNull(request.headers[HttpHeaders.Authorization])
            assertEquals(
                mapOf(
                    "grant_type" to listOf("refresh_token"),
                    "refresh_token" to listOf("old-refresh"),
                    "client_id" to listOf("test-client"),
                    "client_secret" to listOf("test-secret"),
                    "scope" to listOf("tools:read tools:write"),
                    "resource" to listOf(RESOURCE),
                ),
                request.form(),
            )
            val expected = input.clone(
                commonOptions = input.commonOptions.copy(
                    oauth = oauth().copy(
                        accessToken = "new-access",
                        refreshToken = "rotated-refresh",
                        scope = "tools:new",
                        expiresAt = NOW + 120_000,
                    ),
                ),
            )
            assertEquals(expected, refreshed)
            assertEquals(listOf(expected, unrelated), fixture.persisted())
            assertEquals(listOf(expected, unrelated), fixture.store.settingsFlow.value.mcpServers)
            assertEquals(1, fixture.preferences.writes.size)

            val reopened = SettingsStore(fixture.preferences, fixture.scope)
            assertEquals(listOf(expected, unrelated), reopened.settingsFlow.value.mcpServers)
        }
    }

    @Test
    fun `refresh expiry uses response time and keeps missing or nonpositive expiry unknown`() = runTest {
        for ((expiresIn, expected) in listOf(null to 0L, 0L to 0L, -1L to 0L, 120L to NOW + 125_000)) {
            val fixture = fixture()
            fixture.tokenResponse = tokenResponse(expiresIn)
            fixture.beforeTokenResponse = { fixture.clock.milliseconds += 5_000 }

            val refreshed = fixture.coordinator.ensureFreshToken(fixture.current())
            val state = assertNotNull(refreshed.commonOptions.oauth)
            assertEquals(expected, state.expiresAt, "expires_in=$expiresIn")
            assertEquals("old-refresh", state.refreshToken)
            assertEquals("tools:read tools:write", state.scope)
            assertEquals(listOf(refreshed), fixture.persisted())
        }
    }

    @Test
    fun `optional request fields stay omitted and empty returned tokens are not validated`() = runTest {
        for (optional in listOf(null, "", "  ")) {
            val fixture = fixture(server(oauth().copy(clientSecret = optional, scope = optional)))
            fixture.tokenResponse = """{"access_token":"","refresh_token":"","scope":""}"""

            val refreshed = fixture.coordinator.ensureFreshToken(fixture.current())
            val form = fixture.requests.single().form()
            assertFalse("client_secret" in form)
            assertFalse("scope" in form)
            val state = assertNotNull(refreshed.commonOptions.oauth)
            assertEquals("", state.accessToken)
            assertEquals("", state.refreshToken)
            assertEquals("", state.scope)
            assertEquals(0L, state.expiresAt)
            assertEquals(listOf(refreshed), fixture.persisted())
        }
    }

    @Test
    fun `refresh reads the latest stored credentials instead of the stale input`() = runTest {
        val stale = server(oauth())
        val latest = stale.copy(
            commonOptions = stale.commonOptions.copy(oauth = oauth().copy(refreshToken = "latest-refresh")),
        )
        val fixture = fixture(latest)

        val refreshed = fixture.coordinator.ensureFreshToken(stale)
        assertEquals(listOf("latest-refresh"), fixture.requests.single().form()["refresh_token"])
        assertEquals("latest-refresh", refreshed.commonOptions.oauth?.refreshToken)
        assertEquals(listOf(refreshed), fixture.persisted())
    }

    @Test
    fun `a server absent from settings is refreshed without inserting it`() = runTest {
        val unrelated = server(oauth())
        val fixture = fixture(unrelated)
        val input = server(oauth())

        val refreshed = fixture.coordinator.ensureFreshToken(input)
        assertEquals("new-access", refreshed.commonOptions.oauth?.accessToken)
        assertEquals(input.id, refreshed.id)
        assertEquals(1, fixture.requests.size)
        assertEquals(listOf(unrelated), fixture.persisted())
        assertEquals(listOf(unrelated), fixture.store.settingsFlow.value.mcpServers)
    }

    @Test
    fun `http and malformed response failures preserve the original config without retry`() = runTest {
        val cases = listOf(
            HttpStatusCode.BadRequest to "invalid grant",
            HttpStatusCode.Unauthorized to "expired refresh token",
            HttpStatusCode.InternalServerError to "unavailable",
            HttpStatusCode.OK to "not json",
            HttpStatusCode.OK to "{}",
        )
        for ((status, response) in cases) {
            val fixture = fixture()
            fixture.tokenStatus = status
            fixture.tokenResponse = response
            val current = fixture.current()
            val before = fixture.preferences.data.value

            assertSame(current, fixture.coordinator.ensureFreshToken(current))
            assertEquals(1, fixture.requests.size)
            assertTrue(fixture.preferences.writes.isEmpty())
            assertEquals(before, fixture.preferences.data.value)
        }
    }

    @Test
    fun `transport failure and cancellation retain the existing refresh failure behavior`() = runTest {
        for (failure in listOf(IllegalStateException("offline"), CancellationException("cancelled"))) {
            val fixture = fixture()
            fixture.beforeTokenResponse = { throw failure }
            val current = fixture.current()
            val before = fixture.preferences.data.value

            // The existing runCatching also consumes refresh cancellation; this rollback must preserve it.
            assertSame(current, fixture.coordinator.ensureFreshToken(current))
            assertEquals(1, fixture.requests.size)
            assertTrue(fixture.preferences.writes.isEmpty())
            assertEquals(before, fixture.preferences.data.value)
        }
    }

    @Test
    fun `authorization exchange uses the same expiry calculation and retains oauth parameters`() = runTest {
        for ((expiresIn, expected) in listOf(null to 0L, 0L to 0L, -1L to 0L, 120L to NOW + 125_000)) {
            val fixture = fixture(server(oauth().copy(enabled = false, accessToken = null)))
            fixture.tokenResponse = tokenResponse(expiresIn)
            fixture.beforeTokenResponse = { fixture.clock.milliseconds += 5_000 }
            fixture.coordinator.startAuthorization(fixture.current())
            fixture.callbackClosed.await()

            val state = assertNotNull(fixture.current().commonOptions.oauth)
            assertTrue(state.enabled)
            assertEquals("new-access", state.accessToken)
            assertNull(state.refreshToken)
            assertEquals("tools:read tools:write", state.scope)
            assertEquals(expected, state.expiresAt, "expires_in=$expiresIn")
            assertEquals(AUTHORIZATION_ENDPOINT, state.authorizationEndpoint)
            assertEquals(TOKEN_ENDPOINT, state.tokenEndpoint)
            assertEquals(1, fixture.callbackCreations)
            assertEquals(1, fixture.callbackClosures)
            assertEquals(listOf<McpStatus>(McpStatus.Authorizing), fixture.statuses)
            assertEquals(2, fixture.preferences.writes.size)
            assertEquals(fixture.store.settingsFlow.value.mcpServers, fixture.persisted())

            val pending = assertNotNull(fixture.stateAtBrowserLaunch)
            assertTrue(pending.enabled)
            assertNull(pending.accessToken)
            assertEquals(TOKEN_ENDPOINT, pending.tokenEndpoint)

            val authorization = Url(assertNotNull(fixture.authorizationUrl))
            val verifier = Base64.UrlSafe.encode(ByteArray(32) { it.toByte() }).trimEnd('=')
            val challenge = Base64.UrlSafe.encode(PlatformSha256Crypto.digest(verifier.encodeToByteArray()))
                .trimEnd('=')
            assertEquals("code", authorization.parameters["response_type"])
            assertEquals("test-client", authorization.parameters["client_id"])
            assertEquals(REDIRECT_URI, authorization.parameters["redirect_uri"])
            assertEquals("S256", authorization.parameters["code_challenge_method"])
            assertEquals(challenge, authorization.parameters["code_challenge"])
            assertEquals(fixture.expectedState, authorization.parameters["state"])
            assertEquals("tools:read tools:write", authorization.parameters["scope"])
            assertEquals(RESOURCE, authorization.parameters["resource"])

            val request = fixture.requests.single { it.method == HttpMethod.Post }
            assertEquals(TOKEN_ENDPOINT, request.url.toString())
            assertEquals(
                mapOf(
                    "grant_type" to listOf("authorization_code"),
                    "code" to listOf("test-code"),
                    "redirect_uri" to listOf(REDIRECT_URI),
                    "client_id" to listOf("test-client"),
                    "client_secret" to listOf("test-secret"),
                    "code_verifier" to listOf(verifier),
                    "resource" to listOf(RESOURCE),
                ),
                request.form(),
            )
        }
    }

    private fun fixture(vararg servers: McpServerConfig): Fixture =
        Fixture(servers.toList().ifEmpty { listOf(server(oauth())) }).also { fixtures += it }

    private fun server(oauth: McpOAuthState?) = McpServerConfig.StreamableHTTPServer(
        commonOptions = McpCommonOptions(
            name = "测试 MCP",
            headers = listOf("X-Test" to "unchanged"),
            tools = listOf(McpTool(name = "read", enable = false)),
            oauth = oauth,
        ),
        url = "https://MCP.example.test/tools?tenant=one%20two#ignored",
    )

    private fun oauth(expiresAt: Long = NOW + 60_000) = McpOAuthState(
        enabled = true,
        clientId = "test-client",
        clientSecret = "test-secret",
        authorizationEndpoint = AUTHORIZATION_ENDPOINT,
        tokenEndpoint = TOKEN_ENDPOINT,
        scope = "tools:read tools:write",
        accessToken = "old-access",
        refreshToken = "old-refresh",
        expiresAt = expiresAt,
    )

    private class Fixture(servers: List<McpServerConfig>) {
        // Keep platform-client dispatch and the authorization timeout on real dispatchers.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val preferences = MemoryPreferences(servers)
        val store = SettingsStore(preferences, scope)
        val clock = MutableClock()
        val requests = mutableListOf<HttpRequestData>()
        val statuses = mutableListOf<McpStatus>()
        var tokenResponse = tokenResponse(120)
        var tokenStatus = HttpStatusCode.OK
        var beforeTokenResponse: suspend () -> Unit = {}
        var callbackCreations = 0
        var callbackClosures = 0
        var authorizationUrl: String? = null
        var expectedState: String? = null
        var stateAtBrowserLaunch: McpOAuthState? = null
        val callbackClosed = CompletableDeferred<Unit>()
        val engine = MockEngine { request ->
            requests += request
            val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
            when {
                request.url.toString() == TOKEN_ENDPOINT -> {
                    beforeTokenResponse()
                    respond(tokenResponse, tokenStatus, jsonHeaders)
                }
                request.url.encodedPath == "/.well-known/oauth-protected-resource/tools" -> respond(
                    """{"authorization_servers":["https://auth.example.test"]}""",
                    headers = jsonHeaders,
                )
                request.url.encodedPath == "/.well-known/oauth-authorization-server" -> respond(
                    """{"authorization_endpoint":"$AUTHORIZATION_ENDPOINT","token_endpoint":"$TOKEN_ENDPOINT"}""",
                    headers = jsonHeaders,
                )
                request.method == HttpMethod.Get && request.url.encodedPath == "/tools" ->
                    respond("", HttpStatusCode.Unauthorized)
                else -> error("Unexpected request: ${request.method} ${request.url}")
            }
        }
        val client = HttpClient(engine)
        val coordinator = McpOAuthCoordinator(
            settingsStore = store,
            appScope = scope,
            oauthClient = McpOAuthClient(client, PlatformSha256Crypto) { size -> ByteArray(size) { it.toByte() } },
            callbackSessionFactory = OAuthCallbackSessionFactory {
                callbackCreations++
                object : OAuthCallbackSession {
                    override val redirectUri = REDIRECT_URI

                    override suspend fun authorize(authorizationUri: String, expectedState: String): OAuthCallback {
                        authorizationUrl = authorizationUri
                        this@Fixture.expectedState = expectedState
                        stateAtBrowserLaunch = current().commonOptions.oauth
                        return OAuthCallback(state = expectedState, code = "test-code", error = null)
                    }

                    override suspend fun close() {
                        callbackClosures++
                        callbackClosed.complete(Unit)
                    }
                }
            },
            updateStatus = { _, status -> statuses += status },
            clock = clock,
        )

        fun current(): McpServerConfig = store.settingsFlow.value.mcpServers.first()

        fun persisted(): List<McpServerConfig> = JsonInstant.decodeFromString(
            assertNotNull(preferences.data.value[SettingsStore.MCP_SERVERS]),
        )
    }

    private class MutableClock(var milliseconds: Long = NOW) : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(milliseconds)
    }

    private class MemoryPreferences(servers: List<McpServerConfig>) : DataStore<Preferences> {
        override val data = MutableStateFlow(
            preferencesOf(SettingsStore.MCP_SERVERS to JsonInstant.encodeToString(servers)),
        )
        val writes = mutableListOf<Preferences>()

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(data.value).also {
                writes += it
                data.value = it
            }
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val TOKEN_ENDPOINT = "https://auth.example.test/token"
        const val AUTHORIZATION_ENDPOINT = "https://auth.example.test/authorize"
        const val RESOURCE = "https://mcp.example.test/tools?tenant=one%20two"
        const val REDIRECT_URI = "dev.rikkahub.test:/oauth/callback"

        fun tokenResponse(expiresIn: Long?): String = if (expiresIn == null) {
            """{"access_token":"new-access"}"""
        } else {
            """{"access_token":"new-access","expires_in":$expiresIn}"""
        }

        suspend fun HttpRequestData.form(): Map<String, List<String>> =
            parseQueryString(body.toByteArray().decodeToString()).entries().associate { it.key to it.value }
    }
}
