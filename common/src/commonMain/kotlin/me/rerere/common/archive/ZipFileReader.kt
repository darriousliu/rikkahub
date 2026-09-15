package me.rerere.common.archive

import kotlinx.io.files.Path

/** Reads selected entries without extracting or retaining the whole archive. */
expect class ZipFileReader(path: Path) : AutoCloseable {
    fun entries(): List<String>
    fun readEntry(name: String): ByteArray?
    override fun close()
}
