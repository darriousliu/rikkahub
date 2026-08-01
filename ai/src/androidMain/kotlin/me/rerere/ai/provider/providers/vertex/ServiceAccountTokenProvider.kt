package me.rerere.ai.provider.providers.vertex

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.http.content.TextContent
import io.ktor.http.formUrlEncode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.rerere.common.crypto.RsaSha256Signer
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

private val JWT_LIFETIME_SECONDS = 1.hours.inWholeSeconds
private val TOKEN_REFRESH_BUFFER_SECONDS = 5.minutes.inWholeSeconds
private const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"

internal fun interface ServiceAccountTokenTransport {
    suspend fun exchange(assertion: String): ServiceAccountTokenHttpResponse
}

internal data class ServiceAccountTokenHttpResponse(
    val code: Int,
    val body: String,
)

/**
 * 使用服务账号（email + private key PEM）换取 Google OAuth2 Access Token。
 * 构造时传入 HttpClient；调用时传 email、私钥 PEM 与 scopes。
 */
class ServiceAccountTokenProvider internal constructor(
    private val transport: ServiceAccountTokenTransport,
    private val clock: Clock,
    private val rsaSha256Signer: RsaSha256Signer = JdkVertexRsaSha256Signer,
) {
    constructor(http: HttpClient) : this(
        transport = KtorServiceAccountTokenTransport(http),
        clock = Clock.System,
    )

    private val json = Json { ignoreUnknownKeys = true }

    // Token cache to avoid frequent token requests
    private val tokenCache = MutableStateFlow<Map<String, CachedToken>>(emptyMap())

    @Serializable
    private data class CachedToken(
        val token: String,
        val expiresAt: Long // Unix timestamp in seconds
    )

    /**
     * Generate cache key based on service account email and scopes
     */
    private fun generateCacheKey(serviceAccountEmail: String, scopes: List<String>): String {
        return "$serviceAccountEmail:${scopes.sorted().joinToString(",")}"
    }

    /**
     * Check if cached token is still valid (not expired with 5 minutes buffer)
     */
    private fun isCachedTokenValid(cachedToken: CachedToken): Boolean {
        val now = clock.now().epochSeconds
        return cachedToken.expiresAt > (now + TOKEN_REFRESH_BUFFER_SECONDS)
    }

    /**
     * @param serviceAccountEmail  形如 xxx@project-id.iam.gserviceaccount.com
     * @param privateKeyPem        服务账号 JSON 中的 private_key 字段（PKCS#8 PEM, 含 -----BEGIN PRIVATE KEY-----）
     * @param scopes               OAuth scopes，默认 cloud-platform；多个 scope 用 List 传入
     * @return                     access token 字符串
     */
    suspend fun fetchAccessToken(
        serviceAccountEmail: String,
        privateKeyPem: String,
        scopes: List<String> = listOf("https://www.googleapis.com/auth/cloud-platform")
    ): String = withContext(Dispatchers.IO) {
        val cacheKey = generateCacheKey(serviceAccountEmail, scopes)

        // Check cache first
        tokenCache.value[cacheKey]?.let { cachedToken ->
            if (isCachedTokenValid(cachedToken)) {
                return@withContext cachedToken.token
            }
        }
        val now = clock.now().epochSeconds
        val exp = now + JWT_LIFETIME_SECONDS

        val headerJson = """{"alg":"RS256","typ":"JWT"}"""
        val claimJson = """{
          "iss":"$serviceAccountEmail",
          "scope":"${scopes.joinToString(" ")}",
          "aud":"https://oauth2.googleapis.com/token",
          "iat":$now,
          "exp":$exp
        }""".trimIndent()

        val headerB64 = base64UrlNoPad(headerJson.toByteArray(Charsets.UTF_8))
        val claimB64 = base64UrlNoPad(claimJson.toByteArray(Charsets.UTF_8))
        val signingInput = "$headerB64.$claimB64"

        val signature = rsaSha256Signer.signPkcs8Pem(
            privateKeyPem,
            signingInput.toByteArray(Charsets.UTF_8),
        )
        val assertion = "$signingInput.${base64UrlNoPad(signature)}"

        val response = transport.exchange(assertion)
        if (response.code !in 200..299) {
            throw IllegalStateException("Token endpoint ${response.code}: ${response.body}")
        }
        val tokenResp = json.decodeFromString(TokenResponse.serializer(), response.body)
        val accessToken = tokenResp.accessToken ?: error("No access_token in response")

        // Cache the token with expiration time
        val expiresIn = tokenResp.expiresIn ?: JWT_LIFETIME_SECONDS
        val expiresAt = now + expiresIn
        tokenCache.update { it + (cacheKey to CachedToken(accessToken, expiresAt)) }

        accessToken
    }

    @Serializable
    private data class TokenResponse(
        @SerialName("access_token")
        val accessToken: String? = null,
        @SerialName("token_type")
        val tokenType: String? = null,
        @SerialName("expires_in")
        val expiresIn: Long? = null
    )

    private fun base64UrlNoPad(bytes: ByteArray): String =
        Base64.UrlSafe
            .withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)
            .encode(bytes)

}

private class KtorServiceAccountTokenTransport(
    private val http: HttpClient,
    private val tokenEndpoint: String = TOKEN_ENDPOINT,
) : ServiceAccountTokenTransport {
    override suspend fun exchange(assertion: String): ServiceAccountTokenHttpResponse {
        val form = Parameters.build {
            append("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
            append("assertion", assertion)
        }
        val response = http.post(tokenEndpoint) {
            setBody(
                TextContent(
                    text = form.formUrlEncode(),
                    contentType = ContentType.Application.FormUrlEncoded,
                )
            )
        }
        return ServiceAccountTokenHttpResponse(
            code = response.status.value,
            body = response.bodyAsText(),
        )
    }
}

internal fun serviceAccountTokenTransportForTest(tokenEndpoint: String): ServiceAccountTokenTransport =
    KtorServiceAccountTokenTransport(
        http = vertexTokenHttpClientForTest(),
        tokenEndpoint = tokenEndpoint,
    )
