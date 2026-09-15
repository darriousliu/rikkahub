package me.rerere.common.archive

import kotlinx.io.files.Path
import java.util.zip.ZipFile

actual class ZipFileReader actual constructor(path: Path) : AutoCloseable {
    private val zip = ZipFile(path.toString())

    actual fun entries(): List<String> = zip.entries().toList().map { it.name }

    actual fun readEntry(name: String): ByteArray? {
        val entry = zip.getEntry(name) ?: return null
        return zip.getInputStream(entry).use { it.readBytes() }
    }

    actual override fun close() = zip.close()
}
