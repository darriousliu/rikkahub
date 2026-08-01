package me.rerere.rikkahub.data.ai.mcp

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlin.uuid.Uuid

/**
 * MCP UI 可见的运行时边界。
 *
 * 平台实现负责网络连接、OAuth 回调和工具结果文件落盘；共享页面只观察状态并触发用户操作。
 */
interface McpRuntime {
    val syncingStatus: StateFlow<Map<Uuid, McpStatus>>

    fun getStatus(config: McpServerConfig): Flow<McpStatus>

    fun hasClient(config: McpServerConfig): Boolean

    suspend fun syncAll()

    fun startAuthorization(config: McpServerConfig)

    fun cancelAuthorization(config: McpServerConfig)
}
