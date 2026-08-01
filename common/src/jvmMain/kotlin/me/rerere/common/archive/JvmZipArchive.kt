package me.rerere.common.archive

import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.asInputStream
import kotlinx.io.asOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object JvmZipArchive : ZipArchive {
    override fun create(sink: Sink, writeEntries: ZipArchiveWriter.() -> Unit) {
        ZipOutputStream(sink.asOutputStream()).use { zipOutput ->
            val writer = object : ZipArchiveWriter {
                override fun add(name: String, source: Source) {
                    zipOutput.putNextEntry(ZipEntry(name))
                    source.asInputStream().use { input -> input.copyTo(zipOutput) }
                    zipOutput.closeEntry()
                }

                override fun addDirectory(name: String) {
                    val normalized = if (name.endsWith('/')) name else "$name/"
                    zipOutput.putNextEntry(ZipEntry(normalized))
                    zipOutput.closeEntry()
                }
            }
            writer.writeEntries()
        }
    }

    override suspend fun read(source: Source, readEntry: suspend (ZipArchiveEntry) -> Unit) {
        ZipInputStream(source.asInputStream()).use { zipInput ->
            while (true) {
                val entry = zipInput.nextEntry ?: break
                readEntry(
                    object : ZipArchiveEntry {
                        override val name: String = entry.name
                        override val isDirectory: Boolean = entry.isDirectory

                        override fun copyTo(sink: Sink): Long {
                            val output = sink.asOutputStream()
                            val copied = zipInput.copyTo(output)
                            output.flush()
                            return copied
                        }
                    }
                )
                zipInput.closeEntry()
            }
        }
    }
}
