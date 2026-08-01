package me.rerere.rikkahub.data.sync

import java.io.File
import java.nio.file.Files

internal object BackupZipSourcePolicy {
    fun resolveRegularFile(rootDir: File, candidate: File): File? =
        resolve(rootDir, candidate)?.takeIf { it.isFile }

    fun resolveDirectory(rootDir: File, candidate: File): File? =
        resolve(rootDir, candidate)?.takeIf { it.isDirectory }

    private fun resolve(rootDir: File, candidate: File): File? {
        if (Files.isSymbolicLink(rootDir.toPath()) || Files.isSymbolicLink(candidate.toPath())) return null

        val canonicalRoot = rootDir.canonicalFile
        val canonicalCandidate = candidate.canonicalFile
        val candidatePath = canonicalCandidate.path
        val rootPath = canonicalRoot.path
        val isInsideRoot = candidatePath == rootPath || candidatePath.startsWith(rootPath + File.separator)

        return canonicalCandidate.takeIf { isInsideRoot }
    }
}
