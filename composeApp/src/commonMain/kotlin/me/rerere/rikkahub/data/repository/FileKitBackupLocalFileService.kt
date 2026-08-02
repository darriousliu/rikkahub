package me.rerere.rikkahub.data.repository

import io.github.vinceglb.filekit.PlatformFile
import me.rerere.common.logging.RikkaLog as Log
import me.rerere.rikkahub.data.sync.BackupArchiveService
import me.rerere.rikkahub.data.sync.importer.ChatboxImporter
import me.rerere.rikkahub.data.sync.importer.CherryStudioProviderImporter

private const val TAG = "BackupLocalFiles"

class FileKitBackupLocalFileService(
    private val archives: BackupArchiveService,
    private val backupRepository: BackupRepository,
    private val conversationRepository: ConversationRepository,
) : BackupLocalFileService {
    override suspend fun prepareExport(): PlatformFile {
        val archive = archives.prepareArchive(includeDatabase = true, includeFiles = true)
        backupRepository.recordBackupCompleted()
        return archive
    }

    override suspend fun restoreBackup(source: PlatformFile) {
        archives.restoreArchive(source, includeDatabase = true, includeFiles = true)
    }

    override suspend fun restoreChatbox(source: PlatformFile): ChatboxRestoreResult {
        var importedConversations = 0
        var skippedExistingConversations = 0
        val settings = backupRepository.settings.value
        val result = ChatboxImporter.importStreaming(
            file = source,
            assistantId = settings.assistantId,
            providers = settings.providers,
            onConversation = { conversation ->
                if (conversationRepository.existsConversationById(conversation.id)) {
                    skippedExistingConversations++
                } else {
                    conversationRepository.insertConversation(conversation)
                    importedConversations++
                }
            },
        )
        backupRepository.updateSettings(
            settings.copy(
                providers = result.providers + settings.providers,
                assistants = settings.assistants.map { assistant ->
                    if (result.hasConversationSystemPrompt && assistant.id == settings.assistantId) {
                        assistant.copy(allowConversationSystemPrompt = true)
                    } else {
                        assistant
                    }
                },
            )
        )
        Log.i(
            TAG,
            "Imported ${result.providers.size} providers and $importedConversations conversations; " +
                "skipped $skippedExistingConversations existing conversations",
        )
        return ChatboxRestoreResult(
            importedProviders = result.providers.size,
            importedConversations = importedConversations,
            skippedExistingConversations = skippedExistingConversations,
            skippedImageParts = result.skippedImageParts,
            skippedEmptyMessages = result.skippedEmptyMessages,
        )
    }

    override suspend fun restoreCherryStudio(source: PlatformFile) {
        val importedProviders = CherryStudioProviderImporter.importProviders(source)
        require(importedProviders.isNotEmpty()) { "No importable providers found in Cherry Studio backup" }
        val settings = backupRepository.settings.value
        backupRepository.updateSettings(settings.copy(providers = importedProviders + settings.providers))
    }
}
