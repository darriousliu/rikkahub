package me.rerere.rikkahub.ui.theme

import androidx.compose.ui.text.font.FontFamily
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.filesDir
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import io.github.vinceglb.filekit.toKotlinxIoPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path
import kotlinx.io.files.SystemPathSeparator
import me.rerere.rikkahub.data.files.FileFolders
import me.rerere.rikkahub.data.files.getRelativePathInFilesDir
import me.rerere.rikkahub.utils.canonicalFile
import me.rerere.rikkahub.utils.delete
import me.rerere.rikkahub.utils.exists
import me.rerere.rikkahub.utils.isFile
import me.rerere.rikkahub.utils.listFiles
import me.rerere.rikkahub.utils.mkdirs
import me.rerere.rikkahub.utils.renameTo
import me.rerere.rikkahub.utils.writeBytes
import kotlin.time.Clock

data class ImportedChatFont(
    val relativePath: String,
    val displayName: String,
)

class ChatFontRuntime(
    private val filesDir: Path = FileKit.filesDir.toKotlinxIoPath(),
) {
    suspend fun import(source: PlatformFile): Result<ImportedChatFont> = withContext(Dispatchers.IO) {
        runCatching {
            val displayName = source.name.takeIf { it.isNotBlank() } ?: "custom_font"
            val extension = displayName.substringAfterLast('.', "")
                .lowercase()
                .takeIf { it in CUSTOM_FONT_EXTENSIONS }
                ?: "ttf"
            val fontDirectory = Path(filesDir, FileFolders.FONTS).apply { mkdirs() }
            val targetFile = Path(fontDirectory, "chat_font.${Clock.System.now().toEpochMilliseconds()}.$extension")
            val temporaryFile = Path(fontDirectory, "chat_font_import.tmp")

            try {
                temporaryFile.delete()
                temporaryFile.writeBytes(source.readBytes())
                runCatching {
                    loadCustomFont(temporaryFile)
                }.onFailure { error ->
                    throw IllegalArgumentException(error.message ?: "Invalid font file", error)
                }
                replaceCustomChatFont(fontDirectory, temporaryFile, targetFile)
            } catch (error: Throwable) {
                temporaryFile.delete()
                throw error
            }

            val relativePath = getRelativePathInFilesDir(filesDir, targetFile)
                ?: "${FileFolders.FONTS}/${targetFile.name}"
            ImportedChatFont(relativePath = relativePath, displayName = displayName)
        }
    }

    fun delete(relativePath: String): Result<Unit> = runCatching {
        val file = resolveFilesDirFile(relativePath) ?: return@runCatching
        file.delete()
    }

    fun load(relativePath: String): FontFamily? {
        val file = resolveFilesDirFile(relativePath)?.takeIf { it.isFile } ?: return null
        return runCatching {
            loadCustomFont(file)
        }.getOrNull()
    }

    private fun resolveFilesDirFile(relativePath: String): Path? {
        if (relativePath.isBlank()) return null
        val filesDirectory = runCatching { filesDir.canonicalFile }.getOrNull() ?: return null
        val file = runCatching { Path(filesDirectory, relativePath).canonicalFile }.getOrNull() ?: return null
        return file.takeIf { it.toString().startsWith("$filesDirectory$SystemPathSeparator") }
    }
}

private fun replaceCustomChatFont(fontDirectory: Path, temporaryFile: Path, targetFile: Path) {
    val existingFiles = fontDirectory.listFiles()?.filter { file ->
        file.isFile && file.name.startsWith("chat_font.") && file != temporaryFile
    }.orEmpty()
    val backups = existingFiles.map { file ->
        file to Path(fontDirectory, "previous_${file.name}").also { it.delete() }
    }

    try {
        backups.forEach { (file, backup) ->
            check(file.renameTo(backup)) { "Unable to prepare existing font for replacement" }
        }
        check(temporaryFile.renameTo(targetFile)) { "Unable to save selected font" }
        backups.forEach { (_, backup) -> backup.delete() }
    } catch (error: Throwable) {
        temporaryFile.delete()
        backups.forEach { (file, backup) ->
            if (!file.exists() && backup.exists()) backup.renameTo(file)
        }
        throw error
    }
}

private val CUSTOM_FONT_EXTENSIONS = setOf("ttf", "otf", "ttc")

internal expect fun loadCustomFont(file: Path): FontFamily
