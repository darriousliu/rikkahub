package me.rerere.rikkahub.data.ai.mcp

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.rerere.common.concurrent.AtomicSnapshotMap
import me.rerere.common.logging.RikkaLog as Log
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.platform.OAuthCallbackSession
import me.rerere.rikkahub.platform.OAuthCallbackSessionFactory
import me.rerere.rikkahub.platform.requireAuthorizationCode
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

private const val TAG = "McpOAuthCoordinator"
private val OAUTH_CALLBACK_TIMEOUT = 5.minutes

internal class McpOAuthCoordinator(
    private val settingsStore: SettingsStore,
    private val appScope: CoroutineScope,
    private val oauthClient: McpOAuthClient,
    private val callbackSessionFactory: OAuthCallbackSessionFactory,
    private val updateStatus: (Uuid, McpStatus) -> Unit,
    private val tokenPolicy: McpTokenPolicy = McpTokenPolicy(),
) : McpAuthorizationCoordinator {
    private val authorizationJobs = AtomicSnapshotMap<Uuid, Job>()
    private val refreshLocks = AtomicSnapshotMap<Uuid, Mutex>()

    fun startAuthorization(config: McpServerConfig) {
        authorizationJobs.remove(config.id)?.cancel()
        val job = appScope.launch {
            updateStatus(config.id, McpStatus.Authorizing)
            try {
                authorize(config)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "OAuth authorization failed for ${config.commonOptions.name}", error)
                updateStatus(config.id, McpStatus.Error.from(error, fallbackMessage = "OAuth authorization failed"))
            }
        }
        authorizationJobs.put(config.id, job)
        job.invokeOnCompletion { authorizationJobs.remove(config.id, job) }
    }

    fun cancelAuthorization(configId: Uuid) {
        authorizationJobs.remove(configId)?.cancel()
        updateStatus(configId, McpStatus.NeedsAuthorization)
    }

    override fun forget(configId: Uuid) {
        authorizationJobs.remove(configId)?.cancel()
        refreshLocks.remove(configId)
    }

    suspend fun clearAuthorization(config: McpServerConfig): McpServerConfig {
        persistOAuthState(config.id, null)
        return settingsStore.settingsFlow.value.mcpServers.find { it.id == config.id }
            ?: config.clone(commonOptions = config.commonOptions.copy(oauth = null))
    }

    override suspend fun ensureFreshToken(configInput: McpServerConfig): McpServerConfig {
        val lock = refreshLocks.getOrPut(configInput.id) { Mutex() }
        return lock.withLock {
            val config = settingsStore.settingsFlow.value.mcpServers.find { it.id == configInput.id }
                ?: configInput
            val oauth = config.commonOptions.oauth ?: return@withLock config
            if (!tokenPolicy.needsRefresh(oauth)) return@withLock config
            val refreshToken = oauth.refreshToken ?: return@withLock config
            val tokenEndpoint = oauth.tokenEndpoint ?: return@withLock config
            val clientId = oauth.clientId ?: return@withLock config
            runCatching {
                val token = oauthClient.refreshToken(
                    tokenEndpoint = tokenEndpoint,
                    clientId = clientId,
                    clientSecret = oauth.clientSecret,
                    refreshToken = refreshToken,
                    resource = McpOAuthClient.canonicalResource(config.serverUrl),
                    scope = oauth.scope,
                )
                val updated = oauth.copy(
                    accessToken = token.accessToken,
                    refreshToken = token.refreshToken ?: oauth.refreshToken,
                    expiresAt = tokenPolicy.computeExpiry(token.expiresIn),
                    scope = token.scope ?: oauth.scope,
                )
                persistOAuthState(config.id, updated)
                config.clone(commonOptions = config.commonOptions.copy(oauth = updated))
            }.getOrElse {
                Log.w(TAG, "Token refresh failed for ${config.commonOptions.name}: ${it.message}")
                config
            }
        }
    }

    override suspend fun needsAuthorization(config: McpServerConfig, error: Throwable): Boolean {
        if (!looksUnauthorized(error)) return false
        if (config.commonOptions.oauth?.enabled == true) return true
        if (config.commonOptions.headers.any { it.first.equals("Authorization", ignoreCase = true) }) {
            return false
        }
        return runCatching { oauthClient.discoverProtectedResource(config.serverUrl) }
            .onFailure { Log.i(TAG, "OAuth probe failed for ${config.commonOptions.name}: ${it.message}") }
            .isSuccess
    }

    private suspend fun authorize(config: McpServerConfig) {
        val callbackSession = callbackSessionFactory.create()
        try {
            authorize(config, callbackSession)
        } finally {
            withContext(NonCancellable) { callbackSession.close() }
        }
    }

    private suspend fun authorize(config: McpServerConfig, callbackSession: OAuthCallbackSession) {
        val serverUrl = config.serverUrl
        require(serverUrl.isNotBlank()) { "Server URL 为空，无法授权" }
        val redirectUri = callbackSession.redirectUri
        val protectedResource = oauthClient.discoverProtectedResource(serverUrl)
        val issuer = protectedResource.authorizationServers.firstOrNull()
            ?: error("受保护资源未声明授权服务器")
        val metadata = oauthClient.discoverAuthorizationServer(issuer)
        val authorizationEndpoint = metadata.authorizationEndpoint
            ?: error("授权服务器缺少 authorization_endpoint")
        val tokenEndpoint = metadata.tokenEndpoint ?: error("授权服务器缺少 token_endpoint")
        val scope = config.commonOptions.oauth?.scope
            ?: protectedResource.scopesSupported?.joinToString(" ")
            ?: metadata.scopesSupported?.joinToString(" ")

        val existing = config.commonOptions.oauth
        var clientId = existing?.clientId
        var clientSecret = existing?.clientSecret
        if (clientId.isNullOrBlank()) {
            val registrationEndpoint = metadata.registrationEndpoint
                ?: error("授权服务器不支持动态注册，且未预配置 client_id")
            val registration = oauthClient.registerClient(
                registrationEndpoint = registrationEndpoint,
                clientName = config.commonOptions.name,
                redirectUri = redirectUri,
                scope = scope,
            )
            clientId = registration.clientId
            clientSecret = registration.clientSecret
        }

        val pkce = oauthClient.generatePkce()
        val state = oauthClient.generateState()
        val resource = McpOAuthClient.canonicalResource(serverUrl)
        persistOAuthState(
            config.id,
            (existing ?: McpOAuthState()).copy(
                enabled = true,
                clientId = clientId,
                clientSecret = clientSecret,
                authorizationEndpoint = authorizationEndpoint,
                tokenEndpoint = tokenEndpoint,
                registrationEndpoint = metadata.registrationEndpoint,
                scope = scope,
            ),
        )

        val authorizationUrl = oauthClient.buildAuthorizationUrl(
            authorizationEndpoint = authorizationEndpoint,
            clientId = clientId,
            redirectUri = redirectUri,
            pkce = pkce,
            state = state,
            scope = scope,
            resource = resource,
        )
        val callback = withTimeoutOrNull(OAUTH_CALLBACK_TIMEOUT) {
            callbackSession.authorize(authorizationUrl, state)
        } ?: error("OAuth 授权超时")
        val code = callback.requireAuthorizationCode(state)
        val token = oauthClient.exchangeCode(
            tokenEndpoint = tokenEndpoint,
            clientId = clientId,
            clientSecret = clientSecret,
            code = code,
            codeVerifier = pkce.verifier,
            redirectUri = redirectUri,
            resource = resource,
        )
        persistOAuthState(
            config.id,
            McpOAuthState(
                enabled = true,
                clientId = clientId,
                clientSecret = clientSecret,
                authorizationEndpoint = authorizationEndpoint,
                tokenEndpoint = tokenEndpoint,
                registrationEndpoint = metadata.registrationEndpoint,
                scope = token.scope ?: scope,
                accessToken = token.accessToken,
                refreshToken = token.refreshToken,
                expiresAt = tokenPolicy.computeExpiry(token.expiresIn),
            ),
        )
    }

    private suspend fun persistOAuthState(configId: Uuid, oauth: McpOAuthState?) {
        settingsStore.update { old ->
            old.copy(
                mcpServers = old.mcpServers.map { server ->
                    if (server.id != configId) server
                    else server.clone(commonOptions = server.commonOptions.copy(oauth = oauth))
                },
            )
        }
    }

    private fun looksUnauthorized(error: Throwable): Boolean {
        val message = generateSequence(error) { it.cause }
            .mapNotNull { it.message }
            .joinToString(" ")
            .lowercase()
        return message.contains("401") ||
            message.contains("unauthorized") ||
            message.contains("invalid_token") ||
            message.contains("invalid access token") ||
            message.contains("missing or invalid")
    }
}
