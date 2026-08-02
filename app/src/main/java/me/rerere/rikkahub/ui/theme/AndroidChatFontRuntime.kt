package me.rerere.rikkahub.ui.theme

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.files.FileFolders
import me.rerere.rikkahub.data.files.FileUtils
import java.io.File

public class AndroidChatFontRuntime(
    private val context: Context,
) : ChatFontRuntime {
    override val canImportCustomFont: Boolean = true

    override suspend fun import(source: PlatformFile): Result<ImportedChatFont> = withContext(Dispatchers.IO) {
        runCatching {
            val displayName = source.name.takeIf { it.isNotBlank() } ?: "custom_font"
            val extension = displayName.substringAfterLast('.', "")
                .lowercase()
                .takeIf { it in CUSTOM_FONT_EXTENSIONS }
                ?: "ttf"
            val fontDirectory = File(context.filesDir, FileFolders.FONTS).apply { mkdirs() }
            val targetFile = File(fontDirectory, "chat_font.${System.currentTimeMillis()}.$extension")
            val temporaryFile = File(fontDirectory, "chat_font_import.tmp")

            try {
                temporaryFile.delete()
                temporaryFile.writeBytes(source.readBytes())
                Typeface.createFromFile(temporaryFile)
                replaceCustomChatFont(fontDirectory, temporaryFile, targetFile)
            } catch (error: Throwable) {
                temporaryFile.delete()
                throw error
            }

            val relativePath = FileUtils.getRelativePathInFilesDir(context.filesDir, targetFile)
                ?: "${FileFolders.FONTS}/${targetFile.name}"
            ImportedChatFont(relativePath = relativePath, displayName = displayName)
        }
    }

    override fun delete(relativePath: String): Result<Unit> = runCatching {
        val file = resolveFilesDirFile(relativePath) ?: return@runCatching
        check(file.delete() || !file.exists()) { "Unable to delete custom chat font" }
    }

    override fun load(relativePath: String): FontFamily? {
        val file = resolveFilesDirFile(relativePath)?.takeIf { it.isFile } ?: return null
        return runCatching {
            Typeface.createFromFile(file)
            FontFamily(Font(file))
        }.getOrNull()
    }

    private fun resolveFilesDirFile(relativePath: String): File? {
        if (relativePath.isBlank()) return null
        val filesDirectory = runCatching { context.filesDir.canonicalFile }.getOrNull() ?: return null
        val file = runCatching { File(filesDirectory, relativePath).canonicalFile }.getOrNull() ?: return null
        return file.takeIf { it.path.startsWith("${filesDirectory.path}${File.separator}") }
    }
}

private fun replaceCustomChatFont(fontDirectory: File, temporaryFile: File, targetFile: File) {
    val existingFiles = fontDirectory.listFiles { file ->
        file.isFile && file.name.startsWith("chat_font.") && file != temporaryFile
    }?.toList().orEmpty()
    val backups = existingFiles.map { file ->
        file to File(fontDirectory, "previous_${file.name}").also { it.delete() }
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
