package me.rerere.rikkahub.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.rerere.rikkahub.data.db.dao.FolderDAO
import me.rerere.rikkahub.data.db.entity.FolderEntity
import me.rerere.rikkahub.data.model.Folder
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

class FolderRepository(
    private val folderDAO: FolderDAO,
    private val clearConversationFolder: suspend (String) -> Unit,
    clock: Clock = Clock.System,
) {
    private val mapper = FolderPersistenceMapper(clock)

    fun getFoldersOfAssistant(assistantId: Uuid): Flow<List<Folder>> {
        return folderDAO.getFoldersOfAssistant(assistantId.toString())
            .map { list -> list.map(mapper::fromEntity) }
    }

    suspend fun getFolderById(id: Uuid): Folder? {
        return folderDAO.getFolderById(id.toString())?.let(mapper::fromEntity)
    }

    suspend fun createFolder(assistantId: Uuid, name: String): Folder {
        val folder = mapper.create(assistantId, name)
        folderDAO.insert(mapper.toEntity(folder))
        return folder
    }

    suspend fun renameFolder(id: Uuid, name: String) {
        folderDAO.rename(id.toString(), name)
    }

    /**
     * 删除文件夹，先把归属该文件夹的会话 folder_id 清空，再删除文件夹本身（不影响会话）。
     */
    suspend fun deleteFolder(id: Uuid) {
        clearConversationFolder(id.toString())
        folderDAO.deleteById(id.toString())
    }
}

internal class FolderPersistenceMapper(
    private val clock: Clock = Clock.System,
) {
    fun create(assistantId: Uuid, name: String): Folder = Folder(
        assistantId = assistantId,
        name = name,
        createAt = clock.now(),
    )

    fun fromEntity(entity: FolderEntity): Folder = Folder(
        id = Uuid.parse(entity.id),
        assistantId = Uuid.parse(entity.assistantId),
        name = entity.name,
        sortIndex = entity.sortIndex,
        createAt = Instant.fromEpochMilliseconds(entity.createAt),
    )

    fun toEntity(folder: Folder): FolderEntity = FolderEntity(
        id = folder.id.toString(),
        assistantId = folder.assistantId.toString(),
        name = folder.name,
        sortIndex = folder.sortIndex,
        createAt = folder.createAt.toEpochMilliseconds(),
    )
}
