package me.rerere.rikkahub.data.files

import co.touchlab.kermit.Logger
import coil3.Uri
import coil3.toUri
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.extension
import io.github.vinceglb.filekit.filesDir
import io.github.vinceglb.filekit.isRegularFile
import io.github.vinceglb.filekit.lastModified
import io.github.vinceglb.filekit.list
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.parent
import io.github.vinceglb.filekit.resolve
import io.github.vinceglb.filekit.sink
import io.github.vinceglb.filekit.size
import io.github.vinceglb.filekit.source
import io.github.vinceglb.filekit.write
import io.github.vinceglb.filekit.writeString
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.common.PlatformContext
import me.rerere.common.utils.createNewFile
import me.rerere.common.utils.delete
import me.rerere.common.utils.deleteRecursively
import me.rerere.common.utils.mkdirs
import me.rerere.common.utils.toFile
import me.rerere.common.utils.toUri
import me.rerere.rikkahub.data.db.entity.ManagedFileEntity
import me.rerere.rikkahub.data.repository.FilesRepository
import me.rerere.rikkahub.utils.ImageUtils
import me.rerere.rikkahub.utils.exportImage
import me.rerere.rikkahub.utils.exportImageFile
import me.rerere.rikkahub.utils.getFileMimeType
import me.rerere.rikkahub.utils.toImage
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Clock
import kotlin.uuid.Uuid

class FilesManager(
    private val context: PlatformContext,
    private val repository: FilesRepository,
    private val httpClient: HttpClient,
) {
    companion object {
        private const val TAG = "FilesManager"
    }

    suspend fun saveUploadFromUri(
        uri: Uri,
        displayName: String? = null,
        mimeType: String? = null,
    ): ManagedFileEntity = withContext(Dispatchers.IO) {
        val file = uri.toFile()
        val resolvedName = displayName ?: getFileNameFromUri(uri) ?: "file"
        val resolvedMime = mimeType ?: getFileMimeType(uri) ?: "application/octet-stream"
        val target = createTargetFile(FileFolders.UPLOAD, resolvedName)
        file.source().buffered().use {
            target.sink().buffered().use { output ->
                it.transferTo(output)
            }
        }
        val now = Clock.System.now().toEpochMilliseconds()
        repository.insert(
            ManagedFileEntity(
                folder = FileFolders.UPLOAD,
                relativePath = "${FileFolders.UPLOAD}/${target.name}",
                displayName = resolvedName,
                mimeType = resolvedMime,
                sizeBytes = target.size(),
                createdAt = now,
                updatedAt = now,
            )
        )
    }

    suspend fun saveUploadFromBytes(
        bytes: ByteArray,
        displayName: String,
        mimeType: String = "application/octet-stream",
    ): ManagedFileEntity = withContext(Dispatchers.IO) {
        val target = createTargetFile(FileFolders.UPLOAD, displayName)
        target.write(bytes)
        val now = Clock.System.now().toEpochMilliseconds()
        repository.insert(
            ManagedFileEntity(
                folder = FileFolders.UPLOAD,
                relativePath = "${FileFolders.UPLOAD}/${target.name}",
                displayName = displayName,
                mimeType = mimeType,
                sizeBytes = target.size(),
                createdAt = now,
                updatedAt = now,
            )
        )
    }

    suspend fun saveUploadText(
        text: String,
        displayName: String = "pasted_text.txt",
        mimeType: String = "text/plain",
    ): ManagedFileEntity = withContext(Dispatchers.IO) {
        val target = createTargetFile(FileFolders.UPLOAD, displayName)
        target.writeString(text)
        val now = Clock.System.now().toEpochMilliseconds()
        repository.insert(
            ManagedFileEntity(
                folder = FileFolders.UPLOAD,
                relativePath = "${FileFolders.UPLOAD}/${target.name}",
                displayName = displayName,
                mimeType = mimeType,
                sizeBytes = target.size(),
                createdAt = now,
                updatedAt = now,
            )
        )
    }

    suspend fun list(folder: String = FileFolders.UPLOAD): List<ManagedFileEntity> =
        repository.listByFolder(folder)

    suspend fun get(id: Long): ManagedFileEntity? = repository.getById(id)

    fun getFile(entity: ManagedFileEntity): PlatformFile =
        PlatformFile(FileKit.filesDir, entity.relativePath)

    fun createChatFilesByContents(uris: List<Uri>): List<Uri> {
        val newUris = mutableListOf<Uri>()
        val dir = FileKit.filesDir.resolve(FileFolders.UPLOAD)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        uris.forEach { uri ->
            val fileName = Uuid.random()
            val file = dir.resolve("$fileName")
            if (!file.exists()) {
                file.createNewFile()
            }
            val newUri = file.toUri(context)
            runCatching {
                uri.toFile().source().buffered().use { inputStream ->
                    file.sink().buffered().use { outputStream ->
                        inputStream.transferTo(outputStream)
                    }
                }
                newUris.add(newUri)
            }.onFailure {
                it.printStackTrace()
                Logger.e(TAG, it) { "createChatFilesByContents: Failed to save file from $uri" }
            }
        }
        return newUris
    }

    fun createChatFilesByByteArrays(byteArrays: List<ByteArray>): List<Uri> {
        val newUris = mutableListOf<Uri>()
        val dir = FileKit.filesDir.resolve(FileFolders.UPLOAD)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        byteArrays.forEach { byteArray ->
            val fileName = Uuid.random()
            val file = dir.resolve("$fileName")
            if (!file.exists()) {
                file.createNewFile()
            }
            val newUri = file.toUri(context)
            file.sink().buffered().use { outputStream ->
                outputStream.write(byteArray)
            }
            newUris.add(newUri)
        }
        return newUris
    }

    @OptIn(ExperimentalEncodingApi::class)
    suspend fun convertBase64ImagePartToLocalFile(message: UIMessage): UIMessage =
        withContext(Dispatchers.IO) {
            message.copy(
                parts = message.parts.map { part ->
                    when (part) {
                        is UIMessagePart.Image -> {
                            if (part.url.startsWith("data:image")) {
                                val base64Str = part.url.substringAfter("base64,")
                                val sourceByteArray = Base64.decode(base64Str.encodeToByteArray())
                                // 重编码为 PNG
                                val pngBytes = ImageUtils.compressToPng(sourceByteArray)
                                val urls = createChatFilesByByteArrays(listOf(pngBytes))
                                Logger.i(TAG) {
                                    "convertBase64ImagePartToLocalFile: convert base64 img to ${urls.joinToString(", ")}"
                                }
                                part.copy(
                                    url = urls.first().toString(),
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

    fun deleteChatFiles(uris: List<Uri>) {
        uris.filter { it.toString().startsWith("file:") }.forEach { uri ->
            val file = uri.toFile()
            if (file.exists()) {
                file.delete()
            }
        }
    }

    fun deleteAllChatFiles() {
        val dir = FileKit.filesDir.resolve(FileFolders.UPLOAD)
        if (dir.exists()) {
            dir.deleteRecursively()
        }
    }

    suspend fun countChatFiles(): Pair<Int, Long> = withContext(Dispatchers.IO) {
        val dir = FileKit.filesDir.resolve(FileFolders.UPLOAD)
        if (!dir.exists()) {
            return@withContext Pair(0, 0)
        }
        val files = dir.list() ?: return@withContext Pair(0, 0)
        val count = files.size
        val size = files.sumOf { it.size() }
        Pair(count, size)
    }

    suspend fun createChatTextFile(text: String): UIMessagePart.Document {
        val dir = FileKit.filesDir.resolve(FileFolders.UPLOAD)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val fileName = "${Uuid.random()}.txt"
        val file = dir.resolve(fileName)
        file.writeString(text)
        return UIMessagePart.Document(
            url = file.toUri(context).toString(),
            fileName = "pasted_text.txt",
            mime = "text/plain"
        )
    }

    fun getImagesDir(): PlatformFile {
        val dir = FileKit.filesDir.resolve("images")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    @OptIn(ExperimentalEncodingApi::class)
    suspend fun createImageFileFromBase64(base64Data: String, filePath: String): PlatformFile {
        val data = if (base64Data.startsWith("data:image")) {
            base64Data.substringAfter("base64,")
        } else {
            base64Data
        }

        val byteArray = Base64.decode(data.encodeToByteArray())
        val file = PlatformFile(filePath)
        file.parent()?.mkdirs()
        file.write(byteArray)
        return file
    }

    fun listImageFiles(): List<PlatformFile> {
        val imagesDir = getImagesDir()
        return imagesDir.list()
            .filter { it.isRegularFile() && it.extension.lowercase() in listOf("png", "jpg", "jpeg", "webp") }
            .toList()
    }

    @OptIn(ExperimentalEncodingApi::class)
    suspend fun saveMessageImage(activityContext: PlatformContext, image: String) = withContext(Dispatchers.IO) {
        when {
            image.startsWith("data:image") -> {
                val byteArray = Base64.decode(image.substringAfter("base64,").encodeToByteArray())
                val bitmap = byteArray.toImage()
                activityContext.exportImage(activityContext, bitmap)
            }

            image.startsWith("file:") -> {
                val file = image.toUri().toFile()
                activityContext.exportImageFile(activityContext, file)
            }

            image.startsWith("http") -> {
                runCatching {
                    val response = httpClient.get(image)
                    if (response.status == HttpStatusCode.OK) {
                        val bytes = response.bodyAsBytes()
                        val bitmap = bytes.toImage()
                        activityContext.exportImage(activityContext, bitmap)
                    } else {
                        Logger.e(TAG) { "saveMessageImage: Failed to download image from $image, response code: ${response.status.value}" }
                    }
                }.getOrNull()
            }

            else -> error("Invalid image format")
        }
    }

    suspend fun syncFolder(folder: String = FileFolders.UPLOAD): Int = withContext(Dispatchers.IO) {
        val dir = PlatformFile(FileKit.filesDir, folder)
        if (!dir.exists()) return@withContext 0
        val files = dir.list().filter { it.isRegularFile() }
        var inserted = 0
        files.forEach { file ->
            val relativePath = "${folder}/${file.name}"
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
                        sizeBytes = file.size(),
                        createdAt = file.lastModified().toEpochMilliseconds().takeIf { it > 0 } ?: now,
                        updatedAt = now,
                    )
                )
                inserted += 1
            }
        }
        inserted
    }

    suspend fun delete(id: Long, deleteFromDisk: Boolean = true): Boolean = withContext(Dispatchers.IO) {
        val entity = repository.getById(id) ?: return@withContext false
        if (deleteFromDisk) {
            runCatching { getFile(entity).delete() }
        }
        repository.deleteById(id) > 0
    }

    private fun createTargetFile(folder: String, displayName: String): PlatformFile {
        val dir = PlatformFile(FileKit.filesDir, folder)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val ext = displayName.substringAfterLast('.', "")
        val name = if (ext.isNotEmpty() && ext != displayName) {
            "${Uuid.random()}.$ext"
        } else {
            Uuid.random().toString()
        }
        return PlatformFile(dir, name)
    }

    fun getFileNameFromUri(uri: Uri): String? {
        val file = uri.toFile()
        return if (file.exists() && file.isRegularFile()) {
            file.name
        } else {
            null
        }
    }

    fun getFileMimeType(uri: Uri): String? {
        return context.getFileMimeType(uri)
    }

    private fun guessMimeType(file: PlatformFile, fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (ext.isNotEmpty()) {
            return context.getFileMimeType(file.toUri(context))
                ?: "application/octet-stream"
        }
        return sniffMimeType(file)
    }

    private fun sniffMimeType(file: PlatformFile): String {
        val header = ByteArray(16)
        val read = runCatching {
            file.source().buffered().use { input ->
                input.readAtMostTo(header)
            }
        }.getOrDefault(-1)

        if (read <= 0) return "application/octet-stream"

        // Magic numbers
        if (header.startsWithBytes(0x89, 0x50, 0x4E, 0x47)) return "image/png"
        if (header.startsWithBytes(0xFF, 0xD8, 0xFF)) return "image/jpeg"
        if (header.startsWithBytes(0x47, 0x49, 0x46, 0x38)) return "image/gif"
        if (header.startsWithBytes(0x25, 0x50, 0x44, 0x46)) return "application/pdf"
        if (header.startsWithBytes(0x50, 0x4B, 0x03, 0x04)) return "application/zip"
        if (header.startsWithBytes(0x50, 0x4B, 0x05, 0x06)) return "application/zip"
        if (header.startsWithBytes(0x50, 0x4B, 0x07, 0x08)) return "application/zip"
        if (header.startsWithBytes(0x52, 0x49, 0x46, 0x46) && header.sliceArray(8..11)
                .contentEquals(byteArrayOf(0x57, 0x45, 0x42, 0x50))
        ) {
            return "image/webp"
        }

        // Heuristic: treat mostly printable UTF-8 as text/plain
        val textSample = runCatching {
            val sample = ByteArray(512)
            file.source().buffered().use { input ->
                val len = input.readAtMostTo(sample)
                if (len <= 0) return@runCatching null
                sample.copyOf(len)
            }
        }.getOrNull()
        if (textSample != null && isLikelyText(textSample)) {
            return "text/plain"
        }

        return "application/octet-stream"
    }

    private fun isLikelyText(bytes: ByteArray): Boolean {
        var printable = 0
        var total = 0
        bytes.forEach { b ->
            val c = b.toInt() and 0xFF
            total += 1
            if (c == 0x09 || c == 0x0A || c == 0x0D) {
                printable += 1
            } else if (c in 0x20..0x7E) {
                printable += 1
            }
        }
        return total > 0 && printable.toDouble() / total >= 0.8
    }

    private fun ByteArray.startsWithBytes(vararg values: Int): Boolean {
        if (this.size < values.size) return false
        for (i in values.indices) {
            if ((this[i].toInt() and 0xFF) != values[i]) return false
        }
        return true
    }
}

object FileFolders {
    const val UPLOAD = "upload"
}
