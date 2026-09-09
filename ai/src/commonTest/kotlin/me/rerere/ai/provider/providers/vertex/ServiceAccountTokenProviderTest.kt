package me.rerere.ai.provider.providers.vertex

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.parseQueryString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.providers.GoogleProvider
import me.rerere.common.crypto.RsaSha256Signer
import kotlin.io.encoding.Base64
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class ServiceAccountTokenProviderTest {
    private val fixtures = mutableListOf<Fixture>()

    @AfterTest
    fun tearDown() {
        fixtures.forEach {
            it.http.close()
            it.engine.close()
        }
        fixtures.clear()
    }

    @Test
    fun `exchange retains the token URL form JWT claims and real RS256 signature`() = runTest {
        val fixture = fixture()
        assertEquals("access-1", fixture.fetch())

        val request = fixture.requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals(TOKEN_ENDPOINT, request.url.toString())
        assertEquals("application/x-www-form-urlencoded", request.body.contentType.toString())
        assertNull(request.headers[HttpHeaders.Authorization])
        val form = request.form()
        assertEquals(setOf("grant_type", "assertion"), form.keys)
        assertEquals(listOf("urn:ietf:params:oauth:grant-type:jwt-bearer"), form["grant_type"])
        val parts = form.getValue("assertion").single().split('.')
        assertEquals(3, parts.size)
        assertTrue(parts.all { part -> part.all { it.isLetterOrDigit() || it == '-' || it == '_' } })
        assertEquals("""{"alg":"RS256","typ":"JWT"}""", base64.decode(parts[0]).decodeToString())
        val claims = request.claims()
        assertEquals(setOf("iss", "scope", "aud", "iat", "exp"), claims.keys)
        assertEquals(VertexTokenTestData.EMAIL, claims.getValue("iss").jsonPrimitive.content)
        assertEquals(DEFAULT_SCOPE, claims.getValue("scope").jsonPrimitive.content)
        assertEquals(TOKEN_ENDPOINT, claims.getValue("aud").jsonPrimitive.content)
        assertEquals(VertexTokenTestData.NOW, claims.getValue("iat").jsonPrimitive.long)
        assertEquals(VertexTokenTestData.NOW + 3_600, claims.getValue("exp").jsonPrimitive.long)
        assertEquals(VertexTokenTestData.defaultJwtSignature, parts[2])
    }

    @Test
    fun `cache keys sort scopes but JWT preserves scope order and duplicates`() = runTest {
        val fixture = fixture()
        fixture.fetch(scopes = listOf("scope-z", "scope-a", "scope-z"))
        assertEquals("scope-z scope-a scope-z", fixture.requests.single().claims()["scope"]?.jsonPrimitive?.content)

        // A cached token does not re-read or validate a changed private key.
        fixture.fetch(privateKey = "invalid key", scopes = listOf("scope-a", "scope-z", "scope-z"))
        assertEquals(1, fixture.requests.size)
        fixture.fetch(scopes = listOf("scope-a", "scope-z"))
        fixture.fetch(email = "another@example.test", scopes = listOf("scope-a", "scope-z"))
        assertEquals(3, fixture.requests.size)
    }

    @Test
    fun `cached token refreshes exactly at the five minute boundary`() = runTest {
        val fixture = fixture()
        fixture.fetch()
        fixture.clock.seconds += 3_299
        fixture.fetch()
        assertEquals(1, fixture.requests.size)

        fixture.clock.seconds++
        fixture.fetch()
        assertEquals(2, fixture.requests.size)
        assertEquals(VertexTokenTestData.NOW + 3_300, fixture.requests.last().claims()["iat"]?.jsonPrimitive?.long)
        fixture.fetch()
        assertEquals(2, fixture.requests.size)
    }

    @Test
    fun `missing expiry defaults to one hour while short and nonpositive lifetimes are retained`() = runTest {
        for ((expiresIn, calls) in listOf(null to 1, 0L to 2, -1L to 2, 300L to 2, 301L to 1)) {
            val fixture = fixture()
            fixture.responseBody = tokenResponse(expiresIn)
            fixture.fetch()
            fixture.fetch()
            assertEquals(calls, fixture.requests.size, "expires_in=$expiresIn")
            if (expiresIn == null) {
                fixture.clock.seconds += 3_300
                fixture.fetch()
                assertEquals(2, fixture.requests.size)
            }
        }
    }

    @Test
    fun `token expiry is based on request start rather than response time`() = runTest {
        val fixture = fixture()
        fixture.beforeResponse = { fixture.clock.seconds += 3_300 }
        fixture.fetch()
        fixture.beforeResponse = {}
        fixture.fetch()
        assertEquals(2, fixture.requests.size)
    }

    @Test
    fun `successful statuses unknown fields and empty access tokens keep their original behavior`() = runTest {
        for (code in listOf(200, 201, 299)) {
            val fixture = fixture()
            fixture.responseStatus = HttpStatusCode.fromValue(code)
            fixture.responseBody = """{"access_token":"","expires_in":null,"extra":"ignored"}"""
            assertEquals("", fixture.fetch())
            assertEquals("", fixture.fetch())
            assertEquals(1, fixture.requests.size)
        }
    }

    @Test
    fun `http errors retain the full status and body without caching or retrying`() = runTest {
        for (code in listOf(300, 400, 401, 429, 500)) {
            val fixture = fixture()
            fixture.responseStatus = HttpStatusCode.fromValue(code)
            fixture.responseBody = "错误响应\n" + "x".repeat(400)
            val failure = assertFailsWith<IllegalStateException> { fixture.fetch() }
            assertEquals("Token endpoint $code: ${fixture.responseBody}", failure.message)
            assertEquals(1, fixture.requests.size)

            fixture.responseStatus = HttpStatusCode.OK
            fixture.responseBody = tokenResponse(3_600)
            assertEquals("access-1", fixture.fetch())
            assertEquals(2, fixture.requests.size)
        }
    }

    @Test
    fun `missing tokens and malformed responses retain parsing errors and do not enter the cache`() = runTest {
        for (body in listOf("{}", """{"access_token":null}""", "not json", """{"expires_in":{}}""")) {
            val fixture = fixture()
            fixture.responseBody = body
            val failure = assertFails { fixture.fetch() }
            if (body == "{}" || body == """{"access_token":null}""") {
                assertTrue(failure is IllegalStateException)
                assertEquals("No access_token in response", failure.message)
            } else {
                assertTrue(failure is SerializationException)
            }
            assertEquals(1, fixture.requests.size)
            fixture.responseBody = tokenResponse(3_600)
            assertEquals("access-1", fixture.fetch())
            assertEquals(2, fixture.requests.size)
        }
    }

    @Test
    fun `transport errors and cancellation propagate without retry or cache writes`() = runTest {
        for (failure in listOf(IllegalStateException("offline"), CancellationException("cancelled"))) {
            val fixture = fixture()
            fixture.beforeResponse = { throw failure }
            val caught = assertFails { fixture.fetch() }
            assertEquals(failure::class, caught::class)
            assertEquals(failure.message, caught.message)
            assertEquals(1, fixture.requests.size)
            fixture.beforeResponse = {}
            assertEquals("access-1", fixture.fetch())
            assertEquals(2, fixture.requests.size)
        }
    }

    @Test
    fun `signing failures propagate before sending any request and a later call can recover`() = runTest {
        val failure = IllegalArgumentException("test signing failure")
        var failSigning = true
        val realSigner = defaultVertexRsaSha256Signer()
        val fixture = fixture(RsaSha256Signer { pem, data ->
            if (failSigning) throw failure
            realSigner.signPkcs8Pem(pem, data)
        })
        val caught = assertFails { fixture.fetch() }
        assertTrue(caught is IllegalArgumentException)
        assertEquals(failure.message, caught.message)
        // JVM coroutine stack-trace recovery may copy an exception and keep the original as its cause.
        assertTrue(generateSequence<Throwable>(caught) { it.cause }.any { it === failure })
        assertTrue(fixture.requests.isEmpty())
        failSigning = false
        assertEquals("access-1", fixture.fetch())
        assertEquals(1, fixture.requests.size)
    }

    @Test
    fun `concurrent cache misses keep issuing separate requests without added coalescing`() = runTest {
        val fixture = fixture()
        val bothRequests = CompletableDeferred<Unit>()
        fixture.beforeResponse = {
            if (fixture.requests.size == 2) bothRequests.complete(Unit)
            bothRequests.await()
        }
        val results = List(2) { async(Dispatchers.Default) { fixture.fetch() } }.awaitAll()
        assertEquals(listOf("access-1", "access-1"), results)
        assertEquals(2, fixture.requests.size)
        fixture.fetch()
        assertEquals(2, fixture.requests.size)
    }

    @Test
    fun `GoogleProvider uses the default token provider and sends bearer authorization`() = runTest {
        val fixture = fixture()
        val google = GoogleProvider(fixture.http)
        val setting = ProviderSetting.Google(
            vertexAI = true,
            useServiceAccount = true,
            serviceAccountEmail = "  ${VertexTokenTestData.EMAIL}  ",
            privateKey = VertexTokenTestData.privateKeyPem.replace("\n", "\\n"),
            projectId = "test-project",
            location = "test-region",
        )
        val before = Clock.System.now().epochSeconds
        // The model endpoint deliberately returns 503; this checks authorization wiring, not model parsing.
        repeat(2) { assertTrue(google.listModels(setting).isEmpty()) }
        val after = Clock.System.now().epochSeconds
        val tokenRequest = fixture.requests.single { it.url.toString() == TOKEN_ENDPOINT }
        val claims = tokenRequest.claims()
        assertEquals(VertexTokenTestData.EMAIL, claims["iss"]?.jsonPrimitive?.content)
        val issuedAt = claims.getValue("iat").jsonPrimitive.long
        assertTrue(issuedAt in before..after)
        assertEquals(issuedAt + 3_600, claims["exp"]?.jsonPrimitive?.long)
        val modelRequests = fixture.requests.filter { it.url.host == "aiplatform.googleapis.com" }
        assertEquals(2, modelRequests.size)
        modelRequests.forEach {
            assertEquals(HttpMethod.Get, it.method)
            assertEquals(
                "https://aiplatform.googleapis.com/v1/projects/test-project/locations/test-region/models?pageSize=100",
                it.url.toString(),
            )
            assertEquals("Bearer access-1", it.headers[HttpHeaders.Authorization])
            assertNull(it.headers["x-goog-api-key"])
            assertFalse("key" in it.url.parameters)
        }
    }

    private fun fixture(signer: RsaSha256Signer = defaultVertexRsaSha256Signer()): Fixture =
        Fixture(signer).also { fixtures += it }

    private class Fixture(signer: RsaSha256Signer) {
        val clock = MutableClock()
        private val recorded = MutableStateFlow<List<HttpRequestData>>(emptyList())
        val requests get() = recorded.value
        var responseBody = tokenResponse(3_600)
        var responseStatus = HttpStatusCode.OK
        var beforeResponse: suspend () -> Unit = {}
        val engine = MockEngine { request ->
            recorded.update { it + request }
            when (request.url.host) {
                "oauth2.googleapis.com" -> {
                    beforeResponse()
                    respond(responseBody, responseStatus, headersOf(HttpHeaders.ContentType, "application/json"))
                }
                "aiplatform.googleapis.com" ->
                    respond("test model endpoint unavailable", HttpStatusCode.ServiceUnavailable)
                else -> error("Unexpected request: ${request.method} ${request.url}")
            }
        }
        val http = HttpClient(engine)
        val provider = ServiceAccountTokenProvider(
            http = http,
            clock = clock,
            rsaSha256Signer = signer,
        )

        suspend fun fetch(
            email: String = VertexTokenTestData.EMAIL,
            privateKey: String = VertexTokenTestData.privateKeyPem,
            scopes: List<String> = listOf(DEFAULT_SCOPE),
        ) = provider.fetchAccessToken(email, privateKey, scopes)
    }

    private class MutableClock(var seconds: Long = VertexTokenTestData.NOW) : Clock {
        override fun now(): Instant = Instant.fromEpochSeconds(seconds)
    }

    private companion object {
        const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
        const val DEFAULT_SCOPE = "https://www.googleapis.com/auth/cloud-platform"
        val base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

        fun tokenResponse(expiresIn: Long?): String = if (expiresIn == null) {
            """{"access_token":"access-1"}"""
        } else {
            """{"access_token":"access-1","expires_in":$expiresIn}"""
        }

        suspend fun HttpRequestData.form(): Map<String, List<String>> =
            parseQueryString(body.toByteArray().decodeToString()).entries().associate { it.key to it.value }

        suspend fun HttpRequestData.claims(): JsonObject = Json.parseToJsonElement(
            base64.decode(form().getValue("assertion").single().split('.')[1]).decodeToString(),
        ).jsonObject
    }
}
