package me.rerere.rikkahub.platform

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.copyTo
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.div
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.filesDir
import io.github.vinceglb.filekit.mimeType
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.sink
import io.github.vinceglb.filekit.write
import me.rerere.rikkahub.data.files.FileFolders
import me.rerere.rikkahub.data.files.buildUuidFileName

class FileKitPlatformFileStore(
    private val rootDirectory: PlatformFile = FileKit.filesDir,
) {
    suspend fun copyIntoSandbox(source: PlatformFile): Result<PlatformFile> = runCatching {
        val destination = createTargetFile(source.name, source.mimeType()?.toString())
        source.copyTo(destination)
        destination
    }

    suspend fun writeIntoSandbox(bytes: ByteArray, fileName: String): PlatformFile {
        val destination = createTargetFile(fileName, null)
        destination.write(bytes)
        return destination
    }

    private fun createTargetFile(displayName: String, mimeType: String?): PlatformFile {
        val directory = rootDirectory / FileFolders.UPLOAD
        directory.createDirectories()
        val file = directory / buildUuidFileName(displayName, mimeType)
        if (!file.exists()) file.sink().close()
        return file
    }
}
