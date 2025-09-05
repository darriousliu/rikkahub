package me.rerere.ai.provider.providers.vertex

import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.request.forms.formData
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.time.Clock

/**
 * 使用服务账号（email + private key PEM）换取 Google OAuth2 Access Token。
 * 构造时传入 OkHttpClient；调用时传 email、私钥 PEM 与 scopes。
 */
class ServiceAccountTokenProvider(
    private val http: HttpClient
)
{
    private val json = Json { ignoreUnknownKeys = true }
    private val tokenCache = mutableMapOf<String, CachedToken>()

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
        val now = Clock.System.now().epochSeconds
        val bufferSeconds = 300 // 5 minutes buffer before actual expiration
        return cachedToken.expiresAt > (now + bufferSeconds)
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
        val now = Clock.System.now().epochSeconds
        val exp = now + 3600 // 最长 1h

        val headerJson = """{"alg":"RS256","typ":"JWT"}"""
        val claimJson = """{
          "iss":"$serviceAccountEmail",
          "scope":"${scopes.joinToString(" ")}",
          "aud":"https://oauth2.googleapis.com/token",
          "iat":$now,
          "exp":$exp
        }""".trimIndent()

        val headerB64 = base64UrlNoPad(headerJson.encodeToByteArray())
        val claimB64 = base64UrlNoPad(claimJson.encodeToByteArray())
        val signingInput = "$headerB64.$claimB64"

        val privateKey = parsePkcs8PrivateKey(privateKeyPem)
        val signature = signRs256(signingInput.encodeToByteArray(), privateKey)
        val assertion = "$signingInput.${base64UrlNoPad(signature)}"

        val form = formData {
            append("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
            append("assertion", assertion)
        }

        val req = HttpRequestBuilder().apply {
            url("https://oauth2.googleapis.com/token")
            setBody(form)
            header("Content-Type", "application/x-www-form-urlencoded")
        }

        val resp = http.post(req)

        if (!resp.status.isSuccess()) {
            val body = resp.bodyAsText()
            throw IllegalStateException("Token endpoint ${resp.status.value}: $body")
        }

        val body = resp.bodyAsText()
        val tokenResp = json.decodeFromString(TokenResponse.serializer(), body)
        val accessToken = tokenResp.accessToken ?: error("No access_token in response")

        // Cache the token with expiration time
        val expiresIn = tokenResp.expiresIn ?: 3600 // Default 1 hour if not provided
        val expiresAt = now + expiresIn
        tokenCache[cacheKey] = CachedToken(accessToken, expiresAt)

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
        Base64.withPadding(Base64.PaddingOption.ABSENT).encode(bytes)
}

// 平台专用函数声明
expect fun parsePkcs8PrivateKey(pem: String): PrivateKey
expect fun signRs256(data: ByteArray, privateKey: PrivateKey): ByteArray

// 跨平台私钥接口
expect interface PrivateKey
