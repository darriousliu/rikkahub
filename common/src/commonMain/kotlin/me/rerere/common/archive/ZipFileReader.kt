package me.rerere.common.archive

import kotlinx.io.Source
import kotlinx.io.files.Path

/** Reads selected entries without extracting or retaining the whole archive. */
expect class ZipFileReader(path: Path) : AutoCloseable {
    fun entries(): List<String>
    /** The caller must close the entry source before closing the archive. */
    fun openEntry(name: String): Source?
    override fun close()
}
