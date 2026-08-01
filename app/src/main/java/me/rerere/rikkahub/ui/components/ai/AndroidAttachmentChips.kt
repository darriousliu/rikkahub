package me.rerere.rikkahub.ui.components.ai

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.net.toUri
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.ui.hooks.ChatInputState
import org.koin.compose.koinInject

@Composable
internal fun MediaFileInputRow(state: ChatInputState) {
    val filesManager: FilesManager = koinInject()
    val managedFiles by filesManager.observe().collectAsState(initial = emptyList())
    val displayNameByRelativePath = remember(managedFiles) {
        managedFiles.associate { it.relativePath to it.displayName }
    }
    val displayNameByFileName = remember(managedFiles) {
        managedFiles.associate { it.relativePath.substringAfterLast('/') to it.displayName }
    }

    AttachmentInputRow(
        state = state,
        displayNameByRelativePath = displayNameByRelativePath,
        displayNameByFileName = displayNameByFileName,
        onDeleteFile = { url -> filesManager.deleteChatFiles(listOf(url.toUri())) },
    )
}
