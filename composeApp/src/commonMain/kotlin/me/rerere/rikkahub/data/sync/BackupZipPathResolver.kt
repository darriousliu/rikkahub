package me.rerere.rikkahub.data.sync

import kotlinx.io.files.Path
import me.rerere.common.archive.ZipEntryPathPolicy
import me.rerere.rikkahub.utils.canonicalFile
import me.rerere.rikkahub.utils.exists
import me.rerere.rikkahub.utils.mkdirs
import me.rerere.rikkahub.utils.resolve

internal object BackupZipPathResolver {
    fun resolveDirectChild(
        filesDir: Path,
        folderName: String,
        entryPath: String
    ): Path? {
        val fileName = ZipEntryPathPolicy.directChildOfOrNull(entryPath, folderName) ?: return null
        val canonicalFilesDir = filesDir.canonicalFile
        val requestedFolder = Path(canonicalFilesDir, folderName)
        if (!requestedFolder.exists() && !requestedFolder.mkdirs()) return null

        val canonicalFolder = requestedFolder.canonicalFile
        if (canonicalFolder != canonicalFilesDir.resolve(folderName)) return null

        val targetFile = Path(canonicalFolder, fileName).canonicalFile
        return targetFile.takeIf { it.parent == canonicalFolder }
    }
}
