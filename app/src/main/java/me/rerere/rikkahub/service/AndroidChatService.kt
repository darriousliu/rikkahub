package me.rerere.rikkahub.service

import me.rerere.ai.core.Tool
import me.rerere.common.logging.RikkaLog as Log
import me.rerere.rikkahub.data.ai.tools.createWorkspaceTools
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.workspace.WorkspaceShellStatus

suspend fun createWorkspaceToolsIfReady(
    workspaceRepository: WorkspaceRepository,
    workspaceId: String?,
    cwd: String? = null,
): List<Tool> {
    if (workspaceId.isNullOrBlank()) return emptyList()
    val workspace = workspaceRepository.getById(workspaceId) ?: return emptyList()
    if (workspace.shellStatus != WorkspaceShellStatus.READY.name) {
        Log.d(
            "ChatService",
            "createWorkspaceToolsIfReady: skip workspace tools, workspace=$workspaceId, status=${workspace.shellStatus}"
        )
        return emptyList()
    }
    return createWorkspaceTools(workspaceId, workspaceRepository, cwd)
}
