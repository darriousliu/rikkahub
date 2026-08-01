package me.rerere.rikkahub.data.repository

import io.github.vinceglb.filekit.PlatformFile

interface BackupLocalFileService {
    suspend fun prepareExport(): PlatformFile

    suspend fun restoreBackup(source: PlatformFile)

    suspend fun restoreChatbox(source: PlatformFile): ChatboxRestoreResult

    suspend fun restoreCherryStudio(source: PlatformFile)
}

data class ChatboxRestoreResult(
    val importedProviders: Int,
    val importedConversations: Int,
    val skippedExistingConversations: Int,
    val skippedImageParts: Int,
    val skippedEmptyMessages: Int,
)
