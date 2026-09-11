package me.rerere.rikkahub.data.sync

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.cacheDir
import io.github.vinceglb.filekit.div
import io.github.vinceglb.filekit.filesDir
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.parent

data class BackupFileLayout(
    val filesRoot: PlatformFile,
    val cacheRoot: PlatformFile,
    val databaseFiles: Map<String, PlatformFile> = emptyMap(),
) {
    companion object {
        fun create(databaseFile: PlatformFile? = null): BackupFileLayout = BackupFileLayout(
            filesRoot = FileKit.filesDir,
            cacheRoot = FileKit.cacheDir,
            databaseFiles = databaseFile?.let(::databaseArchiveEntries).orEmpty(),
        )

        private fun databaseArchiveEntries(databaseFile: PlatformFile): Map<String, PlatformFile> {
            val parent = requireNotNull(databaseFile.parent()) { "Database file must have a parent directory" }
            return mapOf(
                "rikka_hub.db" to databaseFile,
                "rikka_hub-wal" to (parent / "${databaseFile.name}-wal"),
                "rikka_hub-shm" to (parent / "${databaseFile.name}-shm"),
            )
        }
    }
}
