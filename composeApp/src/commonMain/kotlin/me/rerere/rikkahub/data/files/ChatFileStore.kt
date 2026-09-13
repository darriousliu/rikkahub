package me.rerere.rikkahub.data.files

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import me.rerere.rikkahub.data.db.entity.ManagedFileEntity
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.mimeType
import io.github.vinceglb.filekit.name
import me.rerere.rikkahub.service.toLocalFilePath

/** File operations whose Android implementation also maintains managed-file records. */
interface ChatFileStore {
    fun observe(): Flow<List<ManagedFileEntity>> = flowOf(emptyList())
    fun deleteChatFiles(locations: List<String>)
    fun deleteChatFiles(locations: List<String>, scope: CoroutineScope) = deleteChatFiles(locations)
    suspend fun copyChatFile(location: String): String?
    suspend fun createChatFilesByContents(files: List<PlatformFile>): List<String>
    fun fileFromLocation(location: String): PlatformFile = PlatformFile(location.toLocalFilePath())
    fun getFileName(file: PlatformFile): String? = file.name
    fun getFileMimeType(file: PlatformFile): String? = file.mimeType()?.toString()
}
