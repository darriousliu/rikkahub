package me.rerere.rikkahub.ui.pages.assistant

import androidx.core.net.toUri
import me.rerere.rikkahub.data.files.FilesManager

class AndroidAssistantAssetCleaner(
    private val filesManager: FilesManager,
) : AssistantAssetCleaner {
    override fun deleteLocalAssets(locations: List<String>) {
        filesManager.deleteChatFiles(
            locations
                .filter(::isLocalLocation)
                .map(String::toUri),
        )
    }
}

private fun isLocalLocation(location: String): Boolean =
    location.startsWith("content:") ||
        location.startsWith("file:") ||
        location.startsWith("/")
