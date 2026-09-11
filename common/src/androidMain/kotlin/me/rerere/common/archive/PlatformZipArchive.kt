package me.rerere.common.archive

import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.asInputStream
import kotlinx.io.asOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

actual object PlatformZipArchive : ZipArchive {
    actual override fun create(sink: Sink, writeEntries: ZipArchiveWriter.() -> Unit) {
        ZipOutputStream(sink.asOutputStream()).use { zipOut ->
            val writer = object : ZipArchiveWriter {
                override fun add(name: String, source: Source) {
                    source.asInputStream().use { input ->
                        zipOut.putNextEntry(ZipEntry(normalizeName(name)))
                        input.copyTo(zipOut)
                        zipOut.closeEntry()
                    }
                }

                override fun addDirectory(name: String) {
                    zipOut.putNextEntry(ZipEntry("${normalizeName(name)}/"))
                    zipOut.closeEntry()
                }
            }
            writer.writeEntries()
        }
    }

    actual override suspend fun read(source: Source, readEntry: suspend (ZipArchiveEntry) -> Unit) {
        ZipInputStream(source.asInputStream()).use { zipIn ->
            while (true) {
                val entry = zipIn.nextEntry ?: break
                val normalizedName = normalizeName(entry.name)
                readEntry(object : ZipArchiveEntry {
                    override val name = if (entry.isDirectory) "$normalizedName/" else normalizedName
                    override val isDirectory = entry.isDirectory
                    private var consumed = false

                    override fun copyTo(sink: Sink): Long {
                        check(!consumed) { "ZIP entry content can only be consumed once: $name" }
                        consumed = true
                        return zipIn.copyTo(sink.asOutputStream()).also { sink.flush() }
                    }
                })
                zipIn.closeEntry()
            }
        }
    }

    private fun normalizeName(name: String): String = ZipEntryPathPolicy.normalizeOrNull(name)
        ?: throw ZipArchiveException("Unsafe ZIP entry path: $name")
}
