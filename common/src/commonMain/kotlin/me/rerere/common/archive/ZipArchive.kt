package me.rerere.common.archive

import kotlinx.io.Buffer
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.readByteArray

interface ZipArchive {
    /** Creates an archive and takes ownership of [sink]. */
    fun create(sink: Sink, writeEntries: ZipArchiveWriter.() -> Unit)

    /** Reads an archive and takes ownership of [source]. Entry content is valid only during [readEntry]. */
    fun read(source: Source, readEntry: (ZipArchiveEntry) -> Unit)
}

interface ZipArchiveWriter {
    /** Adds an entry and takes ownership of [source]. */
    fun add(name: String, source: Source)

    fun addDirectory(name: String)
}

interface ZipArchiveEntry {
    val name: String
    val isDirectory: Boolean

    /** Copies the current entry to [sink] without closing it. */
    fun copyTo(sink: Sink): Long
}

fun ZipArchiveWriter.addBytes(name: String, content: ByteArray) {
    add(name, Buffer().apply { write(content) })
}

fun ZipArchiveWriter.addText(name: String, content: String) {
    addBytes(name, content.encodeToByteArray())
}

fun ZipArchiveEntry.readBytes(): ByteArray = Buffer().run {
    copyTo(this)
    readByteArray()
}

fun ZipArchiveEntry.readText(): String = readBytes().decodeToString()
