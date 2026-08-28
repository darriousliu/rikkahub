package me.rerere.rikkahub.platform

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.copyTo
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.delete
import io.github.vinceglb.filekit.filesDir
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.size
import io.github.vinceglb.filekit.write
import io.github.vinceglb.filekit.div
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException

public enum class FileStoreArea(
    internal val directoryName: String,
) {
    IMPORTS("imports"),
    ATTACHMENTS("attachments"),
    IMAGES("images"),
}

public data class StoredPlatformFile(
    val id: Uuid,
    val originalName: String,
    val file: PlatformFile,
    val size: Long,
)

public interface PlatformFileStore {
    public suspend fun copyIntoSandbox(
        source: PlatformFile,
        area: FileStoreArea,
    ): Result<StoredPlatformFile>

    /** Persists [bytes] under [area] using [fileName] as the display name. */
    public suspend fun writeIntoSandbox(
        bytes: ByteArray,
        fileName: String,
        area: FileStoreArea,
    ): Result<StoredPlatformFile>
}

public class FileKitPlatformFileStore(
    private val rootDirectory: PlatformFile = FileKit.filesDir / STORE_DIRECTORY,
) : PlatformFileStore {
    override suspend fun writeIntoSandbox(
        bytes: ByteArray,
        fileName: String,
        area: FileStoreArea,
    ): Result<StoredPlatformFile> = store(area, fileName) { destination -> destination.write(bytes) }

    override suspend fun copyIntoSandbox(
        source: PlatformFile,
        area: FileStoreArea,
    ): Result<StoredPlatformFile> = store(area, source.name) { destination -> source.copyTo(destination) }

    private suspend fun store(
        area: FileStoreArea,
        name: String,
        write: suspend (PlatformFile) -> Unit,
    ): Result<StoredPlatformFile> {
        var destination: PlatformFile? = null
        try {
            val id = Uuid.random()
            val originalName = name.ifBlank { FALLBACK_FILE_NAME }
            val storedName = "$id-${originalName.toSafeFileName()}"
            val areaDirectory = rootDirectory / area.directoryName
            areaDirectory.createDirectories()
            destination = areaDirectory / storedName
            write(destination)
            return Result.success(
                StoredPlatformFile(
                    id = id,
                    originalName = originalName,
                    file = destination,
                    size = destination.size(),
                ),
            )
        } catch (error: Throwable) {
            destination?.let { partialFile ->
                try {
                    partialFile.delete(mustExist = false)
                } catch (_: Throwable) {
                    // Keep the original import error.
                }
            }
            if (error is CancellationException) throw error
            return Result.failure(error)
        }
    }
}

internal fun String.toSafeFileName(): String {
    val sanitized = buildString {
        for (character in this@toSafeFileName) {
            append(
                when {
                    character == '/' || character == '\\' || character.isISOControl() -> '_'
                    else -> character
                },
            )
        }
    }.trim().trim('.')
    return sanitized.ifBlank { FALLBACK_FILE_NAME }.take(MAX_FILE_NAME_LENGTH)
}

private const val STORE_DIRECTORY = "platform-files"
private const val FALLBACK_FILE_NAME = "file"
private const val MAX_FILE_NAME_LENGTH = 120
