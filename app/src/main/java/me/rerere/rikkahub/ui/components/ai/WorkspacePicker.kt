package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Codesandbox
import me.rerere.hugeicons.stroke.ComputerTerminal01
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.hugeicons.stroke.Settings02
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.generated.resources.Res
import me.rerere.rikkahub.generated.resources.assistant_page_workspace
import me.rerere.rikkahub.generated.resources.assistant_page_workspace_unbound
import me.rerere.rikkahub.generated.resources.workspace_detail
import me.rerere.rikkahub.generated.resources.workspace_terminal
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.workspace.WorkspaceShellStatus
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

@Composable
fun WorkspacePicker(
    assistant: Assistant,
    conversation: Conversation,
    onUpdateAssistant: (Assistant) -> Unit,
    onUpdateConversation: (Conversation) -> Unit,
    onDismiss: () -> Unit,
) {
    val workspaceRepository = koinInject<WorkspaceRepository>()
    val workspaces by workspaceRepository.listFlow().collectAsState(initial = emptyList())
    val navController = LocalNavController.current
    if (workspaces.isNotEmpty()) {
        WorkspacePickerListItem(
            assistant = assistant,
            conversation = conversation,
            workspaces = workspaces,
            onUpdateAssistant = onUpdateAssistant,
            onUpdateConversation = onUpdateConversation,
            onNavigateToDetail = { id ->
                onDismiss()
                navController.navigate(Screen.WorkspaceDetail(id))
            },
            onNavigateToTerminal = { id ->
                onDismiss()
                navController.navigate(Screen.WorkspaceTerminal(id))
            },
            onNavigateToManage = {
                onDismiss()
                navController.navigate(Screen.Workspaces)
            },
        )
    }
}

@Composable
fun WorkspaceCwdPicker(
    assistant: Assistant,
    conversation: Conversation,
    onUpdateConversation: (Conversation) -> Unit,
) {
    val workspaceRepository = koinInject<WorkspaceRepository>()
    val workspaces by workspaceRepository.listFlow().collectAsState(initial = emptyList())
    val boundWorkspace = remember(workspaces, assistant.workspaceId) {
        workspaces.find { it.id == assistant.workspaceId?.toString() }
    }
    if (boundWorkspace != null && boundWorkspace.shellStatus == WorkspaceShellStatus.READY.name) {
        var showCwdSheet by remember { mutableStateOf(false) }
        TextButton(
            onClick = { showCwdSheet = true },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        ) {
            Icon(
                imageVector = HugeIcons.Folder01,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = conversation.workspaceCwd ?: "/workspace",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (showCwdSheet) {
            WorkspaceCwdPickerSheet(
                workspaceId = boundWorkspace.id,
                currentCwd = conversation.workspaceCwd,
                onSelectCwd = { newCwd ->
                    onUpdateConversation(conversation.copy(workspaceCwd = newCwd))
                },
                onDismiss = { showCwdSheet = false },
            )
        }
    }
}

@Composable
private fun WorkspacePickerListItem(
    assistant: Assistant,
    conversation: Conversation,
    workspaces: List<WorkspaceEntity>,
    onUpdateAssistant: (Assistant) -> Unit,
    onUpdateConversation: (Conversation) -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToTerminal: (String) -> Unit,
    onNavigateToManage: () -> Unit,
) {
    var showSheet by remember { mutableStateOf(false) }
    val boundWorkspace = remember(workspaces, assistant.workspaceId) {
        workspaces.find { it.id == assistant.workspaceId?.toString() }
    }

    ListItem(
        leadingContent = {
            Icon(
                imageVector = HugeIcons.Codesandbox,
                contentDescription = stringResource(Res.string.assistant_page_workspace),
            )
        },
        headlineContent = {
            Text(stringResource(Res.string.assistant_page_workspace))
        },
        supportingContent = {
            Text(
                text = boundWorkspace?.name ?: stringResource(Res.string.assistant_page_workspace_unbound),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (boundWorkspace != null) {
                    IconButton(onClick = { onNavigateToDetail(boundWorkspace.id) }) {
                        Icon(
                            imageVector = HugeIcons.Settings02,
                            contentDescription = stringResource(Res.string.workspace_detail),
                        )
                    }
                    if (boundWorkspace.shellStatus != WorkspaceShellStatus.DISABLED.name) {
                        IconButton(onClick = { onNavigateToTerminal(boundWorkspace.id) }) {
                            Icon(
                                imageVector = HugeIcons.ComputerTerminal01,
                                contentDescription = stringResource(Res.string.workspace_terminal),
                            )
                        }
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = Modifier
            .clip(MaterialTheme.shapes.large)
            .clickable { showSheet = true },
    )

    if (showSheet) {
        WorkspaceSelectSheet(
            assistant = assistant,
            workspaces = workspaces,
            onSelect = { workspaceId ->
                val newId = workspaceId?.let { Uuid.parse(it) }
                if (newId != assistant.workspaceId) {
                    onUpdateAssistant(assistant.copy(workspaceId = newId))
                    if (conversation.workspaceCwd != null) {
                        onUpdateConversation(conversation.copy(workspaceCwd = null))
                    }
                }
                showSheet = false
            },
            onManage = {
                showSheet = false
                onNavigateToManage()
            },
            onDismiss = { showSheet = false },
        )
    }
}
