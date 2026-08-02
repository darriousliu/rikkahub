package me.rerere.rikkahub.data.ai.mcp

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonObject
import me.rerere.ai.ui.UIMessagePart
import kotlin.uuid.Uuid

data class McpResource(
    val serverId: Uuid,
    val serverName: String,
    val uri: String,
    val name: String,
    val description: String? = null,
    val mimeType: String? = null,
    val size: Long? = null,
)

sealed interface McpResourceContent {
    val uri: String
    val mimeType: String?

    data class Text(
        override val uri: String,
        override val mimeType: String?,
        val text: String,
    ) : McpResourceContent

    data class Blob(
        override val uri: String,
        override val mimeType: String?,
        val bytes: ByteArray,
    ) : McpResourceContent

    data class Unknown(
        override val uri: String,
        override val mimeType: String?,
    ) : McpResourceContent
}

/**
 * MCP UI 可见的运行时边界。
 *
 * 平台实现负责网络连接、OAuth 回调和工具结果文件落盘；共享页面只观察状态并触发用户操作。
 */
interface McpRuntime {
    val syncingStatus: StateFlow<Map<Uuid, McpStatus>>

    fun getStatus(config: McpServerConfig): Flow<McpStatus>

    fun hasClient(config: McpServerConfig): Boolean

    fun getAllAvailableTools(): List<Triple<Uuid, String, McpTool>>

    suspend fun callTool(
        serverId: Uuid,
        toolName: String,
        args: JsonObject,
    ): List<UIMessagePart>

    suspend fun listResources(serverId: Uuid): List<McpResource>

    suspend fun readResource(serverId: Uuid, uri: String): List<McpResourceContent>

    suspend fun syncAll()

    fun startAuthorization(config: McpServerConfig)

    fun cancelAuthorization(config: McpServerConfig)
}
