package me.rerere.rikkahub.data.ai.mcp

import kotlin.uuid.Uuid

interface McpAuthorizationCoordinator {
    fun forget(configId: Uuid)

    suspend fun ensureFreshToken(configInput: McpServerConfig): McpServerConfig

    suspend fun needsAuthorization(config: McpServerConfig, error: Throwable): Boolean
}
