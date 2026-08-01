package me.rerere.rikkahub.data.ai.mcp

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.http.Url
import io.ktor.http.parseQueryString
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Headers.Companion.headersOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class McpOAuthHttpContractTest {
    @Test
    fun `discovers protected resource from bearer challenge`() = runTest {
        MockWebServer().use { server ->
            server.start()
            val metadataUrl = server.url("/resource-metadata").toString()
            server.enqueue(
                MockResponse(
                    code = 401,
                    headers = headersOf(
                        "WWW-Authenticate",
                        "Bearer resource_metadata=\"$metadataUrl\", error=\"invalid_token\"",
                    ),
                )
            )
            server.enqueue(
                jsonResponse(
                    """
                    {
                      "resource": "https://example.com/mcp",
                      "authorization_servers": ["https://auth.example.com/tenant"],
                      "scopes_supported": ["mcp:tools", "mcp:resources"],
                      "ignored": true
                    }
                    """.trimIndent()
                )
            )

            createMcpOAuthClientForContractTest().use { harness ->
                val metadata = harness.client.discoverProtectedResource(server.url("/mcp").toString())

                assertEquals("https://example.com/mcp", metadata.resource)
                assertEquals(listOf("https://auth.example.com/tenant"), metadata.authorizationServers)
                assertEquals(listOf("mcp:tools", "mcp:resources"), metadata.scopesSupported)
            }

            val probe = server.takeRequest()
            assertEquals("GET", probe.method)
            assertEquals("/mcp", probe.url.encodedPath)
            assertEquals("application/json, text/event-stream", probe.headers["Accept"])
            val metadata = server.takeRequest()
            assertEquals("/resource-metadata", metadata.url.encodedPath)
            assertEquals("application/json", metadata.headers["Accept"])
        }
    }

    @Test
    fun `falls back across authorization server well known paths`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse(code = 404, body = "missing oauth metadata"))
            server.enqueue(
                jsonResponse(
                    """
                    {
                      "issuer": "https://auth.example.com/tenant",
                      "authorization_endpoint": "https://auth.example.com/authorize",
                      "token_endpoint": "https://auth.example.com/token",
                      "registration_endpoint": "https://auth.example.com/register",
                      "code_challenge_methods_supported": ["S256"]
                    }
                    """.trimIndent()
                )
            )
            val issuer = server.url("/tenant").toString().trimEnd('/')

            createMcpOAuthClientForContractTest().use { harness ->
                val metadata = harness.client.discoverAuthorizationServer(issuer)

                assertEquals("https://auth.example.com/authorize", metadata.authorizationEndpoint)
                assertEquals("https://auth.example.com/token", metadata.tokenEndpoint)
                assertEquals("https://auth.example.com/register", metadata.registrationEndpoint)
                assertEquals(listOf("S256"), metadata.codeChallengeMethodsSupported)
            }

            assertEquals(
                "/.well-known/oauth-authorization-server/tenant",
                server.takeRequest().url.encodedPath,
            )
            assertEquals(
                "/.well-known/openid-configuration/tenant",
                server.takeRequest().url.encodedPath,
            )
        }
    }

    @Test
    fun `registers a public client with redirect grants and scope`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                jsonResponse(
                    """{"client_id":"registered-client","client_secret":"registered-secret"}"""
                )
            )

            createMcpOAuthClientForContractTest().use { harness ->
                val registered = harness.client.registerClient(
                    registrationEndpoint = server.url("/register").toString(),
                    clientName = "RikkaHub Test",
                    redirectUri = "rikka://mcp/oauth/callback",
                    scope = "mcp:tools mcp:resources",
                )

                assertEquals("registered-client", registered.clientId)
                assertEquals("registered-secret", registered.clientSecret)
            }

            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/register", request.url.encodedPath)
            assertEquals("application/json", request.headers["Accept"])
            assertTrue(request.headers["Content-Type"].orEmpty().startsWith("application/json"))
            val body = Json.parseToJsonElement(request.body?.utf8().orEmpty()).jsonObject
            assertEquals("RikkaHub Test", body.getValue("client_name").jsonPrimitive.content)
            assertEquals(
                "rikka://mcp/oauth/callback",
                body.getValue("redirect_uris").jsonArray.single().jsonPrimitive.content,
            )
            assertEquals(
                listOf("authorization_code", "refresh_token"),
                body.getValue("grant_types").jsonArray.map { it.jsonPrimitive.content },
            )
            assertEquals("none", body.getValue("token_endpoint_auth_method").jsonPrimitive.content)
            assertEquals("mcp:tools mcp:resources", body.getValue("scope").jsonPrimitive.content)
        }
    }

    @Test
    fun `builds authorization url with pkce state scope and canonical resource`() {
        createMcpOAuthClientForContractTest().use { harness ->
            val authorizationUrl = harness.client.buildAuthorizationUrl(
                authorizationEndpoint = "https://AUTH.example.com/authorize?audience=existing",
                clientId = "client id",
                redirectUri = "rikka://mcp/oauth/callback",
                pkce = McpOAuthClient.Pkce(
                    verifier = "test-verifier",
                    challenge = "test-challenge",
                ),
                state = "test-state",
                scope = "mcp:tools mcp:resources",
                resource = "https://example.com/mcp",
            )

            val url = Url(authorizationUrl)
            assertEquals("auth.example.com", url.host)
            assertEquals("existing", url.parameters["audience"])
            assertEquals("code", url.parameters["response_type"])
            assertEquals("client id", url.parameters["client_id"])
            assertEquals("rikka://mcp/oauth/callback", url.parameters["redirect_uri"])
            assertEquals("test-challenge", url.parameters["code_challenge"])
            assertEquals("S256", url.parameters["code_challenge_method"])
            assertEquals("test-state", url.parameters["state"])
            assertEquals("mcp:tools mcp:resources", url.parameters["scope"])
            assertEquals("https://example.com/mcp", url.parameters["resource"])
            assertEquals(
                "https://example.com/mcp?transport=sse",
                McpOAuthClient.canonicalResource(
                    "HTTPS://EXAMPLE.COM:443/mcp?transport=sse#temporary-fragment",
                ),
            )

            val generatedState = harness.client.generateState()
            val generatedPkce = harness.client.generatePkce()
            assertTrue(generatedState.matches(URL_SAFE_VALUE))
            assertTrue(generatedPkce.verifier.matches(URL_SAFE_VALUE))
            assertTrue(generatedPkce.challenge.matches(URL_SAFE_VALUE))
            assertFalse(generatedState.contains('='))
        }
    }

    @Test
    fun `exchanges authorization code with complete form and decodes token`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                jsonResponse(
                    """
                    {
                      "access_token": "access-token",
                      "token_type": "Bearer",
                      "expires_in": 3600,
                      "refresh_token": "refresh-token",
                      "scope": "mcp:tools"
                    }
                    """.trimIndent()
                )
            )

            createMcpOAuthClientForContractTest().use { harness ->
                val token = harness.client.exchangeCode(
                    tokenEndpoint = server.url("/token").toString(),
                    clientId = "client-id",
                    clientSecret = "client-secret",
                    code = "authorization-code",
                    codeVerifier = "pkce-verifier",
                    redirectUri = "rikka://mcp/oauth/callback",
                    resource = "https://example.com/mcp",
                )

                assertEquals("access-token", token.accessToken)
                assertEquals("Bearer", token.tokenType)
                assertEquals(3600L, token.expiresIn)
                assertEquals("refresh-token", token.refreshToken)
                assertEquals("mcp:tools", token.scope)
            }

            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/token", request.url.encodedPath)
            assertEquals("application/json", request.headers["Accept"])
            assertTrue(
                request.headers["Content-Type"].orEmpty()
                    .startsWith("application/x-www-form-urlencoded"),
            )
            val form = parseQueryString(request.body?.utf8().orEmpty())
            assertEquals("authorization_code", form["grant_type"])
            assertEquals("authorization-code", form["code"])
            assertEquals("rikka://mcp/oauth/callback", form["redirect_uri"])
            assertEquals("client-id", form["client_id"])
            assertEquals("client-secret", form["client_secret"])
            assertEquals("pkce-verifier", form["code_verifier"])
            assertEquals("https://example.com/mcp", form["resource"])
        }
    }

    @Test
    fun `refreshes token with optional secret scope and resource`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(jsonResponse("""{"access_token":"refreshed-token"}"""))

            createMcpOAuthClientForContractTest().use { harness ->
                val token = harness.client.refreshToken(
                    tokenEndpoint = server.url("/token").toString(),
                    clientId = "client-id",
                    clientSecret = "client-secret",
                    refreshToken = "old-refresh-token",
                    resource = "https://example.com/mcp",
                    scope = "mcp:tools mcp:resources",
                )
                assertEquals("refreshed-token", token.accessToken)
                assertEquals("Bearer", token.tokenType)
            }

            val form = parseQueryString(server.takeRequest().body?.utf8().orEmpty())
            assertEquals("refresh_token", form["grant_type"])
            assertEquals("old-refresh-token", form["refresh_token"])
            assertEquals("client-id", form["client_id"])
            assertEquals("client-secret", form["client_secret"])
            assertEquals("https://example.com/mcp", form["resource"])
            assertEquals("mcp:tools mcp:resources", form["scope"])
        }
    }

    @Test
    fun `token errors retain status endpoint and bounded response body`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse(code = 400, body = "invalid_grant: authorization code expired"))

            createMcpOAuthClientForContractTest().use { harness ->
                val failure = runCatching {
                    harness.client.exchangeCode(
                        tokenEndpoint = server.url("/token").toString(),
                        clientId = "client-id",
                        clientSecret = null,
                        code = "expired-code",
                        codeVerifier = "pkce-verifier",
                        redirectUri = "rikka://mcp/oauth/callback",
                        resource = "https://example.com/mcp",
                    )
                }.exceptionOrNull()

                assertTrue(failure?.message.orEmpty().contains("HTTP 400"))
                assertTrue(failure?.message.orEmpty().contains("/token"))
                assertTrue(failure?.message.orEmpty().contains("invalid_grant"))
            }
        }
    }

    private fun jsonResponse(body: String): MockResponse = MockResponse(
        code = 200,
        headers = headersOf("Content-Type", "application/json"),
        body = body,
    )

    private companion object {
        val URL_SAFE_VALUE = Regex("[A-Za-z0-9_-]{20,}")
    }
}

private class McpOAuthContractHarness(
    val client: McpOAuthClient,
    private val closeClient: () -> Unit,
) : AutoCloseable {
    override fun close() = closeClient()
}

private fun createMcpOAuthClientForContractTest(): McpOAuthContractHarness {
    val httpClient = HttpClient(OkHttp)
    return McpOAuthContractHarness(
        client = McpOAuthClient(httpClient),
        closeClient = httpClient::close,
    )
}
