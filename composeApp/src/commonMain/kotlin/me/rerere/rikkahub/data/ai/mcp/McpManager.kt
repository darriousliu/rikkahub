package me.rerere.rikkahub.data.ai.mcp

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.sse.SSE
import io.ktor.serialization.kotlinx.json.json
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.types.ImageContent
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.BlobResourceContents
import io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import me.rerere.ai.ui.UIMessagePart
import me.rerere.common.crypto.PlatformSecureRandom
import me.rerere.common.crypto.PlatformSha256Crypto
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.platform.OAuthCallbackSessionFactory
import me.rerere.rikkahub.utils.JsonInstant
import kotlin.io.encoding.Base64
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/** Shared MCP runtime used by Android, iOS and Desktop. */
class McpManager(
    private val settingsStore: SettingsStore,
    private val appScope: CoroutineScope,
    private val imageStore: McpImageStore,
    callbackSessionFactory: OAuthCallbackSessionFactory,
    private val httpClient: HttpClient = createMcpHttpClient(),
) : McpRuntime {
    private val statusStore = McpStatusStore()
    private val oauthCoordinator = McpOAuthCoordinator(
        settingsStore = settingsStore,
        appScope = appScope,
        oauthClient = McpOAuthClient(
            httpClient = httpClient,
            sha256 = PlatformSha256Crypto,
            randomBytes = PlatformSecureRandom::nextBytes,
        ),
        callbackSessionFactory = callbackSessionFactory,
        updateStatus = statusStore::update,
    )
    private val sessionRegistry = McpSessionRegistry(
        settingsStore = settingsStore,
        appScope = appScope,
        httpClient = httpClient,
        oauthCoordinator = oauthCoordinator,
        statusStore = statusStore,
    )

    init {
        appScope.coroutineContext[Job]?.invokeOnCompletion { httpClient.close() }
        appScope.launch {
            settingsStore.settingsFlow
                .map { settings -> settings.mcpServers }
                .distinctUntilChanged()
                .collect(sessionRegistry::reconcile)
        }
    }

    override val syncingStatus: StateFlow<Map<Uuid, McpStatus>>
        get() = statusStore.status

    fun getClient(config: McpServerConfig): Client? = sessionRegistry.getClient(config.id)

    override fun getStatus(config: McpServerConfig): Flow<McpStatus> = sessionRegistry.getStatus(config.id)

    override fun hasClient(config: McpServerConfig): Boolean = getClient(config) != null

    override fun getAllAvailableTools(): List<Triple<Uuid, String, McpTool>> {
        val settings = settingsStore.settingsFlow.value
        val assistant = settings.getCurrentAssistant()
        return settings.mcpServers
            .filter { it.commonOptions.enable && it.id in assistant.mcpServers }
            .flatMap { server ->
                server.commonOptions.tools
                    .filter { tool -> tool.enable }
                    .map { tool -> Triple(server.id, server.commonOptions.name, tool) }
            }
    }

    override suspend fun callTool(
        serverId: Uuid,
        toolName: String,
        args: JsonObject,
    ): List<UIMessagePart> {
        val result = try {
            sessionRegistry.callTool(serverId, toolName, args)
        } catch (error: CancellationException) {
            throw error
        } catch (error: McpClientUnavailableException) {
            return listOf(UIMessagePart.Text("Failed to execute MCP tool: ${error.message.orEmpty()}"))
        }
        return result.content.map { content ->
            when (content) {
                is TextContent -> UIMessagePart.Text(content.text)
                is ImageContent -> imageStore.save(Base64.decode(content.data), content.mimeType)
                else -> UIMessagePart.Text(JsonInstant.encodeToString(content))
            }
        }
    }

    override suspend fun listResources(serverId: Uuid): List<McpResource> {
        val config = settingsStore.settingsFlow.value.mcpServers.find { it.id == serverId }
            ?: throw McpClientUnavailableException("No MCP configuration for server $serverId")
        return sessionRegistry.listResources(serverId).map { resource ->
            McpResource(
                serverId = serverId,
                serverName = config.commonOptions.name,
                uri = resource.uri,
                name = resource.name,
                description = resource.description,
                mimeType = resource.mimeType,
                size = resource.size,
            )
        }
    }

    override suspend fun readResource(serverId: Uuid, uri: String): List<McpResourceContent> =
        sessionRegistry.readResource(serverId, uri).map { content ->
            when (content) {
                is TextResourceContents -> McpResourceContent.Text(
                    uri = content.uri,
                    mimeType = content.mimeType,
                    text = content.text,
                )

                is BlobResourceContents -> McpResourceContent.Blob(
                    uri = content.uri,
                    mimeType = content.mimeType,
                    bytes = Base64.decode(content.blob),
                )

                else -> McpResourceContent.Unknown(
                    uri = content.uri,
                    mimeType = content.mimeType,
                )
            }
        }

    suspend fun addClient(config: McpServerConfig) = sessionRegistry.addClient(config)

    suspend fun removeClient(config: McpServerConfig) = sessionRegistry.removeClient(config)

    override suspend fun syncAll() = sessionRegistry.syncAll()

    override fun startAuthorization(config: McpServerConfig) {
        oauthCoordinator.startAuthorization(config)
    }

    override fun cancelAuthorization(config: McpServerConfig) {
        oauthCoordinator.cancelAuthorization(config.id)
    }

    suspend fun clearAuthorization(config: McpServerConfig) {
        val freshConfig = oauthCoordinator.clearAuthorization(config)
        sessionRegistry.addClient(freshConfig)
    }
}

private fun createMcpHttpClient(): HttpClient = HttpClient {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }
    install(SSE)
    install(HttpTimeout) {
        connectTimeoutMillis = 20.seconds.inWholeMilliseconds
        socketTimeoutMillis = 10.minutes.inWholeMilliseconds
        requestTimeoutMillis = 10.minutes.inWholeMilliseconds
    }
    followRedirects = true
}
