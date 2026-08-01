package me.rerere.rikkahub.data.repository

import android.content.Context
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.source
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.Buffer
import kotlinx.io.asSink
import me.rerere.common.logging.RikkaLog as Log
import me.rerere.rikkahub.data.datastore.WebDavConfig
import me.rerere.rikkahub.data.sync.importer.ChatboxImporter
import me.rerere.rikkahub.data.sync.importer.CherryStudioProviderImporter
import me.rerere.rikkahub.data.sync.webdav.WebDavSync

private const val TAG = "AndroidBackupFiles"

class AndroidBackupLocalFileService(
    private val context: Context,
    private val webDavSync: WebDavSync,
    private val backupRepository: BackupRepository,
    private val conversationRepository: ConversationRepository,
) : BackupLocalFileService {
    override suspend fun prepareExport(): PlatformFile = withContext(Dispatchers.IO) {
        val file = webDavSync.prepareBackupFile(
            backupRepository.settings.value.webDavConfig.copy(items = WebDavConfig.BackupItem.entries)
        )
        try {
            backupRepository.recordBackupCompleted()
            PlatformFile(file)
        } catch (error: Throwable) {
            file.delete()
            throw error
        }
    }

    override suspend fun restoreBackup(source: PlatformFile) {
        withTempFile("restore_", ".zip", source) { file ->
            webDavSync.restoreFromLocalFile(
                file,
                backupRepository.settings.value.webDavConfig.copy(items = WebDavConfig.BackupItem.entries),
            )
        }
    }

    override suspend fun restoreChatbox(source: PlatformFile): ChatboxRestoreResult =
        withTempFile("chatbox_", ".json", source) { file ->
            var importedConversations = 0
            var skippedExistingConversations = 0
            val settings = backupRepository.settings.value
            val result = ChatboxImporter.importStreaming(
                file = file,
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

            val targetAssistantId = settings.assistantId
            backupRepository.updateSettings(
                settings.copy(
                    providers = result.providers + settings.providers,
                    assistants = settings.assistants.map { assistant ->
                        if (result.hasConversationSystemPrompt && assistant.id == targetAssistantId) {
                            assistant.copy(allowConversationSystemPrompt = true)
                        } else {
                            assistant
                        }
                    },
                )
            )
            Log.i(
                TAG,
                "restoreChatbox: imported ${result.providers.size} providers and " +
                    "$importedConversations conversations, skipped $skippedExistingConversations existing",
            )
            ChatboxRestoreResult(
                importedProviders = result.providers.size,
                importedConversations = importedConversations,
                skippedExistingConversations = skippedExistingConversations,
                skippedImageParts = result.skippedImageParts,
                skippedEmptyMessages = result.skippedEmptyMessages,
            )
        }

    override suspend fun restoreCherryStudio(source: PlatformFile) {
        withTempFile("cherry_", ".zip", source) { file ->
            val importedProviders = CherryStudioProviderImporter.importProviders(file)
            require(importedProviders.isNotEmpty()) {
                "No importable providers found in Cherry Studio backup"
            }
            val settings = backupRepository.settings.value
            backupRepository.updateSettings(
                settings.copy(providers = importedProviders + settings.providers)
            )
        }
    }

    private suspend fun <T> withTempFile(
        prefix: String,
        suffix: String,
        source: PlatformFile,
        block: suspend (File) -> T,
    ): T = withContext(Dispatchers.IO) {
        val file = File.createTempFile(prefix, suffix, context.cacheDir)
        try {
            source.source().use { input ->
                FileOutputStream(file).asSink().use { output ->
                    val buffer = Buffer()
                    while (true) {
                        val count = input.readAtMostTo(buffer, 8_192L)
                        if (count == -1L) break
                        output.write(buffer, count)
                    }
                    output.flush()
                }
            }
            block(file)
        } finally {
            file.delete()
        }
    }
}
