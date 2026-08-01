package me.rerere.rikkahub.data.ai.mcp

import me.rerere.common.crypto.Sha256Digest
import me.rerere.common.logging.RikkaLog as Log
import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import io.ktor.http.takeFrom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64

private const val TAG = "McpOAuthClient"

private fun String.toHttpUrlOrNull(): Url? = runCatching { Url(this) }
    .getOrNull()
    ?.takeIf { url ->
        url.host.isNotBlank() && url.protocol in setOf(URLProtocol.HTTP, URLProtocol.HTTPS)
    }

private fun Url.origin(): String = URLBuilder(
    protocol = protocol,
    host = host.lowercase(),
    port = port,
).buildString()

internal fun createPkceChallenge(
    verifier: String,
    sha256: Sha256Digest,
): String {
    val digest = sha256.digest(verifier.encodeToByteArray())
    return Base64.UrlSafe.encode(digest).trimEnd('=')
}

/**
 * MCP OAuth 2.1 授权客户端，实现规范 (2025-11-25 basic/authorization) 所需的各环节：
 *
 * - RFC 9728 受保护资源元数据发现
 * - RFC 8414 / OIDC 授权服务器元数据发现
 * - RFC 7591 动态客户端注册 (DCR)
 * - 带 PKCE (S256) 的授权码流程
 * - RFC 8707 Resource Indicators
 * - 令牌刷新
 *
 * MCP Kotlin SDK 本身不提供 OAuth 支持，因此该逻辑完全独立实现，
 * 最终仅通过 transport 的 requestBuilder 注入 `Authorization: Bearer` 请求头。
 */
class McpOAuthClient(
    private val httpClient: HttpClient,
    private val sha256: Sha256Digest,
    private val randomBytes: (size: Int) -> ByteArray,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
    }

    @Serializable
    data class ProtectedResourceMetadata(
        val resource: String? = null,
        @SerialName("authorization_servers") val authorizationServers: List<String> = emptyList(),
        @SerialName("scopes_supported") val scopesSupported: List<String>? = null,
    )

    @Serializable
    data class AuthorizationServerMetadata(
        val issuer: String? = null,
        @SerialName("authorization_endpoint") val authorizationEndpoint: String? = null,
        @SerialName("token_endpoint") val tokenEndpoint: String? = null,
        @SerialName("registration_endpoint") val registrationEndpoint: String? = null,
        @SerialName("scopes_supported") val scopesSupported: List<String>? = null,
        @SerialName("code_challenge_methods_supported") val codeChallengeMethodsSupported: List<String>? = null,
    )

    @Serializable
    private data class ClientRegistrationRequest(
        @SerialName("client_name") val clientName: String,
        @SerialName("redirect_uris") val redirectUris: List<String>,
        @SerialName("grant_types") val grantTypes: List<String> = listOf("authorization_code", "refresh_token"),
        @SerialName("response_types") val responseTypes: List<String> = listOf("code"),
        @SerialName("token_endpoint_auth_method") val tokenEndpointAuthMethod: String = "none",
        @SerialName("scope") val scope: String? = null,
    )

    @Serializable
    data class ClientRegistrationResponse(
        @SerialName("client_id") val clientId: String,
        @SerialName("client_secret") val clientSecret: String? = null,
    )

    @Serializable
    data class TokenResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("token_type") val tokenType: String = "Bearer",
        @SerialName("expires_in") val expiresIn: Long? = null,
        @SerialName("refresh_token") val refreshToken: String? = null,
        val scope: String? = null,
    )

    /** PKCE 参数对 (code_verifier / code_challenge)。 */
    data class Pkce(val verifier: String, val challenge: String)

    // ---------------------------------------------------------------------
    // 元数据发现
    // ---------------------------------------------------------------------

    /**
     * 发现受保护资源元数据 (RFC 9728)。优先根据服务器 401 响应中的
     * `WWW-Authenticate: resource_metadata="..."` 定位，退回到 well-known 路径。
     */
    suspend fun discoverProtectedResource(serverUrl: String): ProtectedResourceMetadata =
        withContext(Dispatchers.Default) {
            val candidates = buildList {
                probeResourceMetadataUrl(serverUrl)?.let { add(it) }
                addAll(wellKnownPrmUrls(serverUrl))
            }.distinct()
            for (url in candidates) {
                val meta = runCatching { getJson<ProtectedResourceMetadata>(url) }.getOrNull()
                if (meta != null && meta.authorizationServers.isNotEmpty()) {
                    Log.i(TAG, "discoverProtectedResource: found via $url -> ${meta.authorizationServers}")
                    return@withContext meta
                }
            }
            error("无法发现受保护资源元数据 (protected resource metadata)")
        }

    /**
     * 发现授权服务器元数据 (RFC 8414 / OIDC discovery)。依次尝试
     * oauth-authorization-server 与 openid-configuration 的多种 well-known 组合。
     */
    suspend fun discoverAuthorizationServer(issuer: String): AuthorizationServerMetadata =
        withContext(Dispatchers.Default) {
            for (url in wellKnownAsUrls(issuer)) {
                val meta = runCatching { getJson<AuthorizationServerMetadata>(url) }.getOrNull()
                if (meta?.authorizationEndpoint != null && meta.tokenEndpoint != null) {
                    Log.i(TAG, "discoverAuthorizationServer: found via $url")
                    return@withContext meta
                }
            }
            error("无法发现授权服务器元数据 (authorization server metadata): $issuer")
        }

    /** 动态客户端注册 (RFC 7591)，返回 client_id (公共客户端通常无 secret)。 */
    suspend fun registerClient(
        registrationEndpoint: String,
        clientName: String,
        redirectUri: String,
        scope: String?,
    ): ClientRegistrationResponse = withContext(Dispatchers.Default) {
        val body = json.encodeToString(
            ClientRegistrationRequest.serializer(),
            ClientRegistrationRequest(
                clientName = clientName.ifBlank { "RikkaHub" },
                redirectUris = listOf(redirectUri),
                scope = scope,
            )
        )
        val text = execute(
            url = registrationEndpoint,
            response = httpClient.post(registrationEndpoint) {
                accept(ContentType.Application.Json)
                contentType(ContentType.Application.Json)
                setBody(body)
            },
        )
        json.decodeFromString(ClientRegistrationResponse.serializer(), text)
    }

    // ---------------------------------------------------------------------
    // 授权码流程
    // ---------------------------------------------------------------------

    fun generatePkce(): Pkce {
        val verifierBytes = randomBytes(32).also { require(it.size == 32) }
        val verifier = base64Url(verifierBytes)
        return Pkce(verifier = verifier, challenge = createPkceChallenge(verifier, sha256))
    }

    fun generateState(): String {
        val bytes = randomBytes(16).also { require(it.size == 16) }
        return base64Url(bytes)
    }

    /** 拼接授权端点 URL，附带 PKCE、state 以及 RFC 8707 resource 参数。 */
    fun buildAuthorizationUrl(
        authorizationEndpoint: String,
        clientId: String,
        redirectUri: String,
        pkce: Pkce,
        state: String,
        scope: String?,
        resource: String,
    ): String {
        val base = authorizationEndpoint.toHttpUrlOrNull()
            ?: error("非法的授权端点: $authorizationEndpoint")
        return URLBuilder().takeFrom(base).apply {
            host = host.lowercase()
            parameters.append("response_type", "code")
            parameters.append("client_id", clientId)
            parameters.append("redirect_uri", redirectUri)
            parameters.append("code_challenge", pkce.challenge)
            parameters.append("code_challenge_method", "S256")
            parameters.append("state", state)
            parameters.append("resource", resource)
            if (!scope.isNullOrBlank()) parameters.append("scope", scope)
        }.buildString()
    }

    /** 用授权码换取访问令牌。 */
    suspend fun exchangeCode(
        tokenEndpoint: String,
        clientId: String,
        clientSecret: String?,
        code: String,
        codeVerifier: String,
        redirectUri: String,
        resource: String,
    ): TokenResponse = withContext(Dispatchers.Default) {
        val form = parameters {
            append("grant_type", "authorization_code")
            append("code", code)
            append("redirect_uri", redirectUri)
            append("client_id", clientId)
            append("code_verifier", codeVerifier)
            append("resource", resource)
            if (!clientSecret.isNullOrBlank()) append("client_secret", clientSecret)
        }
        postToken(tokenEndpoint, form)
    }

    /** 使用 refresh_token 刷新访问令牌。 */
    suspend fun refreshToken(
        tokenEndpoint: String,
        clientId: String,
        clientSecret: String?,
        refreshToken: String,
        resource: String,
        scope: String?,
    ): TokenResponse = withContext(Dispatchers.Default) {
        val form = parameters {
            append("grant_type", "refresh_token")
            append("refresh_token", refreshToken)
            append("client_id", clientId)
            append("resource", resource)
            if (!clientSecret.isNullOrBlank()) append("client_secret", clientSecret)
            if (!scope.isNullOrBlank()) append("scope", scope)
        }
        postToken(tokenEndpoint, form)
    }

    private suspend fun postToken(tokenEndpoint: String, form: Parameters): TokenResponse {
        val text = execute(
            url = tokenEndpoint,
            response = httpClient.post(tokenEndpoint) {
                accept(ContentType.Application.Json)
                setBody(FormDataContent(form))
            },
        )
        return json.decodeFromString(TokenResponse.serializer(), text)
    }

    // ---------------------------------------------------------------------
    // 内部工具
    // ---------------------------------------------------------------------

    /** 向 MCP Server 发一次探测请求，从 401 的 WWW-Authenticate 提取 resource_metadata。 */
    private suspend fun probeResourceMetadataUrl(serverUrl: String): String? {
        return runCatching {
            val response = httpClient.get(serverUrl) {
                header(HttpHeaders.Accept, "application/json, text/event-stream")
            }
            response.bodyAsText()
            if (response.status.value != 401) return null
            val header = response.headers[HttpHeaders.WWWAuthenticate] ?: return null
            parseResourceMetadata(header)
        }.getOrNull()
    }

    private fun parseResourceMetadata(wwwAuthenticate: String): String? {
        // 例如: Bearer resource_metadata="https://host/.well-known/oauth-protected-resource", error="..."
        val regex = Regex("resource_metadata=\"([^\"]+)\"")
        return regex.find(wwwAuthenticate)?.groupValues?.getOrNull(1)
    }

    private fun wellKnownPrmUrls(serverUrl: String): List<String> {
        val url = serverUrl.toHttpUrlOrNull() ?: return emptyList()
        val origin = url.origin()
        val path = url.encodedPath.trimEnd('/')
        return buildList {
            if (path.isNotEmpty() && path != "/") {
                add("$origin/.well-known/oauth-protected-resource$path")
            }
            add("$origin/.well-known/oauth-protected-resource")
        }.distinct()
    }

    private fun wellKnownAsUrls(issuer: String): List<String> {
        val url = issuer.toHttpUrlOrNull() ?: return emptyList()
        val origin = url.origin()
        val path = url.encodedPath.trimEnd('/')
        return buildList {
            if (path.isNotEmpty() && path != "/") {
                add("$origin/.well-known/oauth-authorization-server$path")
                add("$origin/.well-known/openid-configuration$path")
                add("$origin$path/.well-known/openid-configuration")
            }
            add("$origin/.well-known/oauth-authorization-server")
            add("$origin/.well-known/openid-configuration")
        }.distinct()
    }

    private suspend inline fun <reified T> getJson(url: String): T {
        val text = execute(
            url = url,
            response = httpClient.get(url) {
                accept(ContentType.Application.Json)
            },
        )
        return json.decodeFromString(text)
    }

    private suspend fun execute(url: String, response: HttpResponse): String {
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw IllegalStateException("HTTP ${response.status.value} for $url: ${body.take(300)}")
        }
        return body
    }

    companion object {
        private fun base64Url(bytes: ByteArray): String =
            Base64.UrlSafe.encode(bytes).trimEnd('=')

        /**
         * 规范化 canonical resource URI (RFC 8707 + MCP 规范)：小写 scheme/host、去掉 fragment。
         */
        fun canonicalResource(serverUrl: String): String {
            val url = serverUrl.toHttpUrlOrNull() ?: return serverUrl
            return URLBuilder().takeFrom(url).apply {
                host = host.lowercase()
                encodedFragment = ""
            }.buildString()
        }
    }
}
