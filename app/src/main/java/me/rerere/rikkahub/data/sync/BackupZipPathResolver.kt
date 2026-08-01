package me.rerere.rikkahub.data.sync

import me.rerere.common.archive.ZipEntryPathPolicy
import java.io.File

internal object BackupZipPathResolver {
    fun resolveDirectChild(
        filesDir: File,
        folderName: String,
        entryPath: String
    ): File? {
        val fileName = ZipEntryPathPolicy.directChildOfOrNull(entryPath, folderName) ?: return null
        val canonicalFilesDir = filesDir.canonicalFile
        val requestedFolder = File(canonicalFilesDir, folderName)
        if (!requestedFolder.exists() && !requestedFolder.mkdirs()) return null

        val canonicalFolder = requestedFolder.canonicalFile
        if (canonicalFolder.parentFile != canonicalFilesDir) return null

        val targetFile = File(canonicalFolder, fileName).canonicalFile
        return targetFile.takeIf { it.parentFile == canonicalFolder }
    }
}
