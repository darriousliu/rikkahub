package me.rerere.rikkahub.web

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.filesDir
import kotlinx.coroutines.CoroutineScope
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.FolderRepository
import me.rerere.rikkahub.generated.resources.Res
import me.rerere.rikkahub.platform.JvmJmDnsServiceRegistrar
import me.rerere.rikkahub.service.ChatService
import java.io.File

fun createJvmWebServerRuntime(
    scope: CoroutineScope,
    filesDir: File = FileKit.filesDir.file,
    chatService: ChatService,
    conversationRepo: ConversationRepository,
    folderRepo: FolderRepository,
    settingsStore: SettingsStore,
    filesManager: FilesManager,
): WebServerRuntime =
    WebServerManager(
        host = KtorWebServerHost {
            configureWebApi(filesDir, chatService, conversationRepo, folderRepo, settingsStore, filesManager) {
                Res.readBytes("files/$it").inputStream()
            }
        },
        appScope = scope,
        nsdRegistrar = JvmJmDnsServiceRegistrar(),
    )
