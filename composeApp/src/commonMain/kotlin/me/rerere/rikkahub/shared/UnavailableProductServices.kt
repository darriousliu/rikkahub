package me.rerere.rikkahub.shared

import androidx.paging.PagingData
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import me.rerere.rikkahub.data.ai.mcp.McpRuntime
import me.rerere.rikkahub.data.ai.mcp.McpServerConfig
import me.rerere.rikkahub.data.ai.mcp.McpStatus
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.files.SkillStore
import me.rerere.rikkahub.data.files.SkillSummary
import me.rerere.rikkahub.data.files.StoredSkillFile
import me.rerere.rikkahub.data.repository.BackupLocalFileService
import me.rerere.rikkahub.data.repository.ChatboxRestoreResult
import me.rerere.rikkahub.data.sync.S3BackupItem
import me.rerere.rikkahub.data.sync.S3BackupTransport
import me.rerere.rikkahub.data.sync.WebDavBackupTransport
import me.rerere.rikkahub.data.sync.s3.S3Config
import me.rerere.rikkahub.data.sync.webdav.WebDavBackupItem
import me.rerere.rikkahub.service.ImageGenerationRequest
import me.rerere.rikkahub.service.ImageGenerationRuntime
import me.rerere.rikkahub.service.ImageGenerationUpdate
import me.rerere.rikkahub.ui.pages.imggen.GeneratedImage
import kotlin.uuid.Uuid

private fun unavailable(feature: String): Nothing =
    throw UnsupportedOperationException("$feature is unavailable on ${currentPlatformKind.displayName}")

internal object EmptySkillStore : SkillStore {
    override suspend fun listSkills(): List<SkillSummary> = emptyList()

    override suspend fun saveSkill(name: String, content: String): Boolean = false

    override suspend fun saveSkillFiles(name: String, files: Map<String, String>): Boolean = false

    override suspend fun saveSkillFileBytes(name: String, files: Map<String, ByteArray>): Boolean = false

    override suspend fun deleteSkill(name: String): Boolean = false

    override suspend fun listSkillFiles(name: String): List<StoredSkillFile> = emptyList()

    override suspend fun readSkillFile(name: String, relativePath: String): String? = null

    override suspend fun saveSkillFile(name: String, relativePath: String, content: String): Boolean = false

    override suspend fun deleteSkillFile(name: String, relativePath: String): Boolean = false
}

internal object UnavailableMcpRuntime : McpRuntime {
    override val syncingStatus: StateFlow<Map<Uuid, McpStatus>> = MutableStateFlow(emptyMap())

    override fun getStatus(config: McpServerConfig): Flow<McpStatus> =
        flowOf(McpStatus.Error("MCP is unavailable on ${currentPlatformKind.displayName}"))

    override fun hasClient(config: McpServerConfig): Boolean = false

    override suspend fun syncAll() = Unit

    override fun startAuthorization(config: McpServerConfig) = Unit

    override fun cancelAuthorization(config: McpServerConfig) = Unit
}

internal object UnavailableWebDavBackupTransport : WebDavBackupTransport {
    override suspend fun testConnection(config: me.rerere.rikkahub.data.datastore.WebDavConfig): Int =
        unavailable("WebDAV backup")

    override suspend fun backup(config: me.rerere.rikkahub.data.datastore.WebDavConfig): Boolean =
        unavailable("WebDAV backup")

    override suspend fun listBackupFiles(
        config: me.rerere.rikkahub.data.datastore.WebDavConfig,
    ): List<WebDavBackupItem> = unavailable("WebDAV backup")

    override suspend fun restore(
        config: me.rerere.rikkahub.data.datastore.WebDavConfig,
        item: WebDavBackupItem,
    ): Int = unavailable("WebDAV backup")

    override suspend fun deleteBackupFile(
        config: me.rerere.rikkahub.data.datastore.WebDavConfig,
        item: WebDavBackupItem,
    ): Int = unavailable("WebDAV backup")
}

internal object UnavailableS3BackupTransport : S3BackupTransport {
    override suspend fun testS3(config: S3Config): Int = unavailable("S3 backup")

    override suspend fun backupToS3(config: S3Config): Boolean = unavailable("S3 backup")

    override suspend fun listBackupFiles(config: S3Config): List<S3BackupItem> = unavailable("S3 backup")

    override suspend fun restoreFromS3(config: S3Config, item: S3BackupItem): Int = unavailable("S3 backup")

    override suspend fun deleteS3BackupFile(config: S3Config, item: S3BackupItem): Int = unavailable("S3 backup")
}

internal object UnavailableBackupLocalFileService : BackupLocalFileService {
    override suspend fun prepareExport(): PlatformFile = unavailable("Local backup")

    override suspend fun restoreBackup(source: PlatformFile) = unavailable("Local backup")

    override suspend fun restoreChatbox(source: PlatformFile): ChatboxRestoreResult = unavailable("Local backup")

    override suspend fun restoreCherryStudio(source: PlatformFile) = unavailable("Local backup")
}

internal class UnavailableImageGenerationRuntime(
    private val settingsStore: SettingsStore,
) : ImageGenerationRuntime {
    override val settingsFlow: StateFlow<Settings> = settingsStore.settingsFlow

    override fun generatedImages(): Flow<PagingData<GeneratedImage>> = flowOf(PagingData.empty())

    override suspend fun updateSettings(settings: Settings) = settingsStore.update(settings)

    override suspend fun importReferenceImages(files: List<PlatformFile>): List<String> =
        unavailable("Image generation")

    override suspend fun deleteTemporaryFiles(paths: List<String>) = Unit

    override fun generateImage(request: ImageGenerationRequest): Flow<ImageGenerationUpdate> =
        flow { unavailable("Image generation") }

    override fun editImage(request: ImageGenerationRequest): Flow<ImageGenerationUpdate> =
        flow { unavailable("Image generation") }

    override suspend fun deleteImage(image: GeneratedImage) = unavailable("Image generation")
}
