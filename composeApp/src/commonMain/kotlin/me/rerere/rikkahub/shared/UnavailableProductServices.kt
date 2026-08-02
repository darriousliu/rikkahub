package me.rerere.rikkahub.shared

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import me.rerere.rikkahub.data.ai.mcp.McpRuntime
import me.rerere.rikkahub.data.ai.mcp.McpServerConfig
import me.rerere.rikkahub.data.ai.mcp.McpStatus
import kotlin.uuid.Uuid

private fun unavailable(feature: String): Nothing =
    throw UnsupportedOperationException("$feature is unavailable on ${currentPlatformKind.displayName}")

internal object UnavailableMcpRuntime : McpRuntime {
    override val syncingStatus: StateFlow<Map<Uuid, McpStatus>> = MutableStateFlow(emptyMap())

    override fun getStatus(config: McpServerConfig): Flow<McpStatus> =
        flowOf(McpStatus.Error("MCP is unavailable on ${currentPlatformKind.displayName}"))

    override fun hasClient(config: McpServerConfig): Boolean = false

    override suspend fun syncAll() = Unit

    override fun startAuthorization(config: McpServerConfig) = Unit

    override fun cancelAuthorization(config: McpServerConfig) = Unit
}
