package me.rerere.ai.provider.providers.vertex

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.rerere.common.crypto.RsaSha256Signer
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

private val JWT_LIFETIME_SECONDS = 1.hours.inWholeSeconds
private val TOKEN_REFRESH_BUFFER_SECONDS = 5.minutes.inWholeSeconds

/**
 * 使用服务账号（email + private key PEM）换取 Google OAuth2 Access Token。
 * 构造时传入 OkHttpClient；调用时传 email、私钥 PEM 与 scopes。
 */
class ServiceAccountTokenProvider internal constructor(
    private val http: OkHttpClient,
    private val clock: Clock,
    private val rsaSha256Signer: RsaSha256Signer = JdkVertexRsaSha256Signer,
) {
    constructor(http: OkHttpClient) : this(
        http = http,
        clock = Clock.System,
    )

    private val json = Json { ignoreUnknownKeys = true }

    // Token cache to avoid frequent token requests
    private val tokenCache = ConcurrentHashMap<String, CachedToken>()

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
        tokenCache[cacheKey]?.let { cachedToken ->
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

        val form = FormBody.Builder()
            .add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
            .add("assertion", assertion)
            .build()

        val req = Request.Builder()
            .url("https://oauth2.googleapis.com/token")
            .post(form)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .build()

        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val body = resp.body.string()
                throw IllegalStateException("Token endpoint ${resp.code}: $body")
            }
            val body = resp.body.string()
            val tokenResp = json.decodeFromString(TokenResponse.serializer(), body)
            val accessToken = tokenResp.accessToken ?: error("No access_token in response")

            // Cache the token with expiration time
            val expiresIn = tokenResp.expiresIn ?: JWT_LIFETIME_SECONDS
            val expiresAt = now + expiresIn
            tokenCache[cacheKey] = CachedToken(accessToken, expiresAt)

            accessToken
        }
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
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

}
