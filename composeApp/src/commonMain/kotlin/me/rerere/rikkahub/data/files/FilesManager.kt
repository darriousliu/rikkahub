package me.rerere.rikkahub.data.files

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.filesDir
import io.github.vinceglb.filekit.toKotlinxIoPath
import io.github.vinceglb.filekit.copyTo
import io.github.vinceglb.filekit.delete
import io.github.vinceglb.filekit.write
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.IO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.common.logging.Logging
import me.rerere.common.logging.RikkaLog as Log
import me.rerere.rikkahub.data.db.entity.ManagedFileEntity
import me.rerere.rikkahub.data.repository.FilesRepository
import me.rerere.rikkahub.platform.FileKitFileCleaner
import me.rerere.rikkahub.platform.encodeImageToPng
import me.rerere.rikkahub.service.toLocalFilePath
import me.rerere.rikkahub.utils.delete
import me.rerere.rikkahub.utils.deleteRecursively
import me.rerere.rikkahub.utils.exists
import me.rerere.rikkahub.utils.isFile
import me.rerere.rikkahub.utils.lastModified
import me.rerere.rikkahub.utils.length
import me.rerere.rikkahub.utils.listFiles
import me.rerere.rikkahub.utils.mkdirs
import me.rerere.rikkahub.utils.resolve
import me.rerere.rikkahub.utils.writeBytes
import me.rerere.rikkahub.utils.writeText
import kotlin.io.encoding.Base64
import kotlin.time.Clock

class FilesManager(
    private val filesDir: Path = FileKit.filesDir.toKotlinxIoPath(),
    private val repository: FilesRepository,
    private val appScope: CoroutineScope,
    private val legacyFileCleaner: FileKitFileCleaner? = null,
    private val asyncFileIo: Boolean = false,
) {
    companion object {
        private const val TAG = "FilesManager"
    }

    suspend fun saveManagedFromUri(
        folder: String,
        uri: PlatformFile,
        displayName: String? = null,
        mimeType: String? = null,
    ): ManagedFileEntity = withContext(Dispatchers.IO) {
        val resolvedName = displayName ?: getFileName(uri) ?: "file"
        val resolvedMime = mimeType ?: getFileMimeType(uri) ?: "application/octet-stream"
        val target = createTargetFile(folder, resolvedName, resolvedMime)
        openFileSource(uri)?.use { input ->
            SystemFileSystem.sink(target).buffered().use { output ->
                output.transferFrom(input)
            }
        }
        createManagedFileEntity(
            folder = folder,
            file = target,
            displayName = resolvedName,
            mimeType = resolvedMime,
        )
    }

    suspend fun saveManagedFromBytes(
        folder: String,
        bytes: ByteArray,
        displayName: String,
        mimeType: String = "application/octet-stream",
    ): ManagedFileEntity = withContext(Dispatchers.IO) {
        val target = createTargetFile(folder, displayName, mimeType)
        target.writeBytes(bytes)
        createManagedFileEntity(
            folder = folder,
            file = target,
            displayName = displayName,
            mimeType = mimeType,
        )
    }

    suspend fun saveManagedText(
        folder: String,
        text: String,
        displayName: String = "pasted_text.txt",
        mimeType: String = "text/plain",
    ): ManagedFileEntity = withContext(Dispatchers.IO) {
        val target = createTargetFile(folder, displayName, mimeType)
        target.writeText(text)
        createManagedFileEntity(
            folder = folder,
            file = target,
            displayName = displayName,
            mimeType = mimeType,
        )
    }

    fun observe(folder: String = FileFolders.UPLOAD): Flow<List<ManagedFileEntity>> =
        repository.listByFolder(folder)

    suspend fun list(folder: String = FileFolders.UPLOAD): List<ManagedFileEntity> =
        repository.listByFolder(folder).first()

    suspend fun get(id: Long): ManagedFileEntity? = repository.getById(id)

    suspend fun getByRelativePath(relativePath: String): ManagedFileEntity? = repository.getByPath(relativePath)

    fun getFile(entity: ManagedFileEntity): Path =
        Path(filesDir, entity.relativePath)

    fun createChatFilesByContents(uris: List<PlatformFile>): List<String> =
        createChatFilesByContents(uris) { uri, file ->
            val inputStream = openFileSource(uri)
                ?: error("Failed to open input stream for ${fileLocation(uri)}")
            inputStream.use { input ->
                SystemFileSystem.sink(file).buffered().use { output -> output.transferFrom(input) }
            }
        }

    private inline fun createChatFilesByContents(
        uris: List<PlatformFile>,
        copyContents: (PlatformFile, Path) -> Unit,
    ): List<String> {
        val newUris = mutableListOf<String>()
        val dir = filesDir.resolve(FileFolders.UPLOAD)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        uris.forEach { uri ->
            runCatching {
                val sourceName = getFileName(uri) ?: fileNameFallback(uri) ?: "file"
                val sourceMime = getFileMimeType(uri)
                val fileName = buildUuidFileName(displayName = sourceName, mimeType = sourceMime)
                val file = dir.resolve(fileName)
                if (!file.exists()) {
                    SystemFileSystem.sink(file).close()
                }
                copyContents(uri, file)
                val guessedMime = sourceMime ?: guessMimeType(file, sourceName)
                trackManagedFile(
                    folder = FileFolders.UPLOAD,
                    file = file,
                    displayName = sourceName,
                    mimeType = guessedMime
                )
                newUris.add(file.toFileUri())
            }.onFailure {
                it.printStackTrace()
                Log.e(TAG, "createChatFilesByContents: Failed to save file from ${fileLocation(uri)}", it)
                Logging.log(
                    TAG,
                    "createChatFilesByContents: Failed to save file from ${fileLocation(uri)} ${it.message} | ${it.stackTraceToString()}"
                )
            }
        }
        return newUris
    }

    fun createChatFilesByByteArrays(byteArrays: List<ByteArray>): List<String> =
        createChatFilesByByteArrays(byteArrays) { file, bytes -> file.writeBytes(bytes) }

    private inline fun createChatFilesByByteArrays(
        byteArrays: List<ByteArray>,
        writeContents: (Path, ByteArray) -> Unit,
    ): List<String> {
        val newUris = mutableListOf<String>()
        val dir = filesDir.resolve(FileFolders.UPLOAD)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        byteArrays.forEach { byteArray ->
            val fileName = buildUuidFileName(displayName = "image.png", mimeType = "image/png")
            val file = dir.resolve(fileName)
            if (!file.exists()) {
                SystemFileSystem.sink(file).close()
            }
            val newUri = file.toFileUri()
            writeContents(file, byteArray)
            trackManagedFile(
                folder = FileFolders.UPLOAD,
                file = file,
                displayName = "image.png",
                mimeType = "image/png"
            )
            newUris.add(newUri)
        }
        return newUris
    }

    suspend fun convertBase64ImagePartToLocalFile(message: UIMessage): UIMessage =
        withContext(Dispatchers.IO) {
            message.copy(
                parts = message.parts.map { part ->
                    when (part) {
                        is UIMessagePart.Image -> {
                            if (part.url.startsWith("data:image")) {
                                val sourceByteArray = Base64.decode(part.url.substringAfter("base64,").encodeToByteArray())
                                val byteArray = encodeImageToPng(sourceByteArray)!!
                                val urls = if (asyncFileIo) {
                                    createChatFilesByByteArrays(listOf(byteArray)) { file, bytes ->
                                        PlatformFile(file.toString()).write(bytes)
                                    }
                                } else {
                                    createChatFilesByByteArrays(listOf(byteArray))
                                }
                                Log.i(
                                    TAG,
                                    "convertBase64ImagePartToLocalFile: convert base64 img to ${urls.joinToString(", ")}"
                                )
                                part.copy(
                                    url = urls.first(),
                                )
                            } else {
                                part
                            }
                        }

                        else -> part
                    }
                }
            )
        }

    fun deleteChatFiles(uris: List<String>) {
        if (asyncFileIo) {
            appScope.launch { deleteChatFilesAsync(uris) }
        } else {
            deleteChatFilesNow(uris)
        }
    }

    private fun deleteChatFilesNow(uris: List<String>) {
        val relativePaths = mutableSetOf<String>()
        uris.filter { it.startsWith("file:") }.forEach { uri ->
            val file = Path(uri.toLocalFilePath())
            getRelativePathInFilesDir(filesDir, file)?.let { relativePaths.add(it) }
            if (file.exists()) {
                file.delete()
            }
        }
        if (relativePaths.isNotEmpty()) {
            appScope.launch(Dispatchers.IO) {
                relativePaths.forEach { path ->
                    repository.deleteByPath(path)
                }
            }
        }
    }

    suspend fun countChatFiles(): Pair<Int, Long> = withContext(Dispatchers.IO) {
        val dir = filesDir.resolve(FileFolders.UPLOAD)
        if (!dir.exists()) {
            return@withContext Pair(0, 0)
        }
        val files = dir.listFiles() ?: return@withContext Pair(0, 0)
        val count = files.size
        val size = files.sumOf { it.length() }
        Pair(count, size)
    }

    fun createChatTextFile(text: String): UIMessagePart.Document {
        val dir = filesDir.resolve(FileFolders.UPLOAD)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val fileName = buildUuidFileName(displayName = "pasted_text.txt", mimeType = "text/plain")
        val file = dir.resolve(fileName)
        file.writeText(text)
        trackManagedFile(
            folder = FileFolders.UPLOAD,
            file = file,
            displayName = "pasted_text.txt",
            mimeType = "text/plain"
        )
        return UIMessagePart.Document(
            url = file.toFileUri(),
            fileName = "pasted_text.txt",
            mime = "text/plain"
        )
    }

    fun getImagesDir(): Path {
        val dir = filesDir.resolve("images")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun createImageFileFromBase64(base64Data: String, filePath: String): Path {
        me.rerere.rikkahub.data.files.createImageFileFromBase64(base64Data, kotlinx.io.files.Path(filePath))
        return Path(filePath)
    }

    fun listImageFiles(): List<Path> {
        val imagesDir = getImagesDir()
        return imagesDir.listFiles()
            ?.filter { it.isFile && it.name.substringAfterLast('.', "").lowercase() in listOf("png", "jpg", "jpeg", "webp") }
            ?.toList()
            ?: emptyList()
    }

    suspend fun syncFolder(folder: String = FileFolders.UPLOAD): SyncResult = withContext(Dispatchers.IO) {
        val dir = Path(filesDir, folder)
        val diskFiles = if (dir.exists()) {
            dir.listFiles()?.filter { it.isFile }
                ?: return@withContext SyncResult(inserted = 0, removed = 0)
        } else {
            emptyList()
        }

        // 磁盘 -> 数据库：补录尚未登记的文件
        var inserted = 0
        val diskRelativePaths = HashSet<String>()
        diskFiles.forEach { file ->
            val relativePath = "${folder}/${file.name}"
            diskRelativePaths.add(relativePath)
            val existing = repository.getByPath(relativePath)
            if (existing == null) {
                val now = Clock.System.now().toEpochMilliseconds()
                val displayName = file.name
                val mimeType = guessMimeType(file, displayName)
                repository.insert(
                    ManagedFileEntity(
                        folder = folder,
                        relativePath = relativePath,
                        displayName = displayName,
                        mimeType = mimeType,
                        sizeBytes = file.length(),
                        createdAt = file.lastModified().takeIf { it > 0 } ?: now,
                        updatedAt = now,
                    )
                )
                inserted += 1
            }
        }

        // 数据库 -> 磁盘：清理文件已不存在的孤儿记录
        var removed = 0
        repository.listByFolder(folder).first().forEach { entity ->
            if (entity.relativePath !in diskRelativePaths && !getFile(entity).isFile) {
                removed += repository.deleteByPath(entity.relativePath)
            }
        }

        SyncResult(inserted = inserted, removed = removed)
    }

    suspend fun delete(id: Long, deleteFromDisk: Boolean = true): Boolean = withContext(Dispatchers.IO) {
        val entity = repository.getById(id) ?: return@withContext false
        if (deleteFromDisk) {
            runCatching { getFile(entity).delete() }
        }
        repository.deleteById(id) > 0
    }

    suspend fun deleteAll(folder: String = FileFolders.UPLOAD): Boolean = withContext(Dispatchers.IO) {
        val dir = Path(filesDir, folder)
        val entries = dir.listFiles()
        if (dir.exists() && entries == null) {
            return@withContext false
        }

        var allDeletedFromDisk = true
        entries.orEmpty().forEach { entry ->
            if (!runCatching { entry.deleteRecursively() }.getOrDefault(false)) {
                allDeletedFromDisk = false
            }
        }

        if (allDeletedFromDisk) {
            repository.deleteByFolder(folder)
            return@withContext true
        }

        repository.listByFolder(folder).first().forEach { entity ->
            if (!getFile(entity).exists()) {
                repository.deleteById(entity.id)
            }
        }
        false
    }

    private fun createTargetFile(folder: String, displayName: String, mimeType: String?): Path {
        val dir = Path(filesDir, folder)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return Path(dir, buildUuidFileName(displayName = displayName, mimeType = mimeType))
    }

    private suspend fun createManagedFileEntity(
        folder: String,
        file: Path,
        displayName: String,
        mimeType: String,
    ): ManagedFileEntity {
        val now = Clock.System.now().toEpochMilliseconds()
        return repository.insert(
            ManagedFileEntity(
                folder = folder,
                relativePath = buildRelativePath(folder, file),
                displayName = displayName,
                mimeType = mimeType,
                sizeBytes = file.length(),
                createdAt = now,
                updatedAt = now,
            )
        )
    }

    private fun trackManagedFile(folder: String, file: Path, displayName: String, mimeType: String) {
        val relativePath = buildRelativePath(folder, file)
        appScope.launch(Dispatchers.IO) {
            runCatching {
                val existing = repository.getByPath(relativePath)
                if (existing != null) {
                    return@runCatching
                }
                val now = Clock.System.now().toEpochMilliseconds()
                repository.insert(
                    ManagedFileEntity(
                        folder = folder,
                        relativePath = relativePath,
                        displayName = displayName,
                        mimeType = mimeType,
                        sizeBytes = file.length(),
                        createdAt = now,
                        updatedAt = now,
                    )
                )
            }.onFailure {
                Log.e(TAG, "trackManagedFile: Failed to track file ${file}", it)
                Logging.log(
                    TAG,
                    "trackManagedFile: Failed to track file ${file} ${it.message} | ${it.stackTraceToString()}"
                )
            }
        }
    }

    fun fileFromLocation(location: String): PlatformFile = platformFileFromLocation(location)

    fun getFileName(file: PlatformFile): String? = fileDisplayName(file)

    fun getFileMimeType(file: PlatformFile): String? = fileMimeType(file)

    suspend fun importChatFiles(files: List<PlatformFile>): List<String> = if (asyncFileIo) {
        createChatFilesByContents(files) { source, destination ->
            source.copyTo(PlatformFile(destination.toString()))
        }
    } else {
        createChatFilesByContents(files)
    }

    suspend fun copyChatFile(location: String): String? =
        importChatFiles(listOf(fileFromLocation(location))).firstOrNull()

    fun deleteChatFiles(locations: List<String>, scope: CoroutineScope) {
        // Preserve Android's synchronous callbacks and the shared callers' cancellation scope.
        if (!asyncFileIo) {
            deleteChatFilesNow(locations)
        } else {
            scope.launch { deleteChatFilesAsync(locations) }
        }
    }

    suspend fun deleteConversationFiles(locations: List<String>) {
        if (legacyFileCleaner == null) {
            deleteChatFiles(locations)
        } else {
            legacyFileCleaner.deleteChatFiles(locations)
            untrackDeletedFiles(locations)
        }
    }

    suspend fun deleteLocalAssets(locations: List<String>) {
        if (legacyFileCleaner == null) {
            deleteChatFiles(locations)
        } else {
            legacyFileCleaner.deleteLocalAssets(locations)
            untrackDeletedFiles(locations)
        }
    }

    private suspend fun deleteChatFilesAsync(locations: List<String>) {
        locations.forEach { location ->
            try {
                PlatformFile(location.toLocalFilePath()).delete(mustExist = false)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
            }
        }
        untrackDeletedFiles(locations)
    }

    private fun untrackDeletedFiles(locations: List<String>) {
        deleteChatFilesNow(
            locations.filter { it.startsWith("file:") || it.startsWith("/") }
                .map { Path(it.toLocalFilePath()) }
                .filterNot { it.exists() }
                .map { it.toFileUri() }
        )
    }

}

data class SyncResult(
    val inserted: Int,
    val removed: Int,
)

suspend fun FilesManager.saveUploadFromUri(
    uri: PlatformFile,
    displayName: String? = null,
    mimeType: String? = null,
): ManagedFileEntity = saveManagedFromUri(
    folder = FileFolders.UPLOAD,
    uri = uri,
    displayName = displayName,
    mimeType = mimeType,
)

suspend fun FilesManager.saveUploadFromBytes(
    bytes: ByteArray,
    displayName: String,
    mimeType: String = "application/octet-stream",
): ManagedFileEntity = saveManagedFromBytes(
    folder = FileFolders.UPLOAD,
    bytes = bytes,
    displayName = displayName,
    mimeType = mimeType,
)

suspend fun FilesManager.saveUploadText(
    text: String,
    displayName: String = "pasted_text.txt",
    mimeType: String = "text/plain",
): ManagedFileEntity = saveManagedText(
    folder = FileFolders.UPLOAD,
    text = text,
    displayName = displayName,
    mimeType = mimeType,
)
