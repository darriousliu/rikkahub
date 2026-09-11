package me.rerere.rikkahub.data.sync

import kotlinx.io.files.Path
import kotlinx.io.files.SystemPathSeparator
import me.rerere.rikkahub.utils.canonicalFile
import me.rerere.rikkahub.utils.isDirectory
import me.rerere.rikkahub.utils.isFile
import me.rerere.rikkahub.utils.isSymbolicLink

internal object BackupZipSourcePolicy {
    fun resolveRegularFile(rootDir: Path, candidate: Path): Path? =
        resolve(rootDir, candidate)?.takeIf { it.isFile }

    fun resolveDirectory(rootDir: Path, candidate: Path): Path? =
        resolve(rootDir, candidate)?.takeIf { it.isDirectory }

    private fun resolve(rootDir: Path, candidate: Path): Path? {
        if (rootDir.isSymbolicLink() || candidate.isSymbolicLink()) return null

        val canonicalRoot = rootDir.canonicalFile
        val canonicalCandidate = candidate.canonicalFile
        val candidatePath = canonicalCandidate.toString()
        val rootPath = canonicalRoot.toString()
        val isInsideRoot = candidatePath == rootPath || candidatePath.startsWith(rootPath + SystemPathSeparator)

        return canonicalCandidate.takeIf { isInsideRoot }
    }
}
