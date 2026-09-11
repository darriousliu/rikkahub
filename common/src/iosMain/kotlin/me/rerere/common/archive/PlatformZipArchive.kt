@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package me.rerere.common.archive

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.value
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import swiftPMImport.rikkahub.common.RKHZipArchive

actual object PlatformZipArchive : ZipArchive {
    actual override fun create(sink: Sink, writeEntries: ZipArchiveWriter.() -> Unit) {
        sink.use {
            withTemporaryDirectory { directory ->
                val archivePath = Path(directory, "archive.zip")
                val entryPath = Path(directory, "entry")
                val zip = checked { RKHZipArchive.openAtPath(archivePath.toString(), writing = true, error = it) }!!
                try {
                    val entryNames = mutableSetOf<String>()
                    val writer = object : ZipArchiveWriter {
                        override fun add(name: String, source: Source) {
                            source.use {
                                val normalizedName = normalizeName(name)
                                if (!entryNames.add(normalizedName)) {
                                    throw ZipArchiveException("Duplicate ZIP entry: $normalizedName")
                                }
                                // ZIPFoundation needs the entry size before writing its local header.
                                SystemFileSystem.sink(entryPath).buffered().use { source.transferTo(it) }
                                try {
                                    checked {
                                        zip.addFileNamed(normalizedName, fromPath = entryPath.toString(), error = it)
                                    }
                                } finally {
                                    SystemFileSystem.delete(entryPath)
                                }
                            }
                        }

                        override fun addDirectory(name: String) {
                            val normalizedName = "${normalizeName(name)}/"
                            if (!entryNames.add(normalizedName)) {
                                throw ZipArchiveException("Duplicate ZIP entry: $normalizedName")
                            }
                            checked { zip.addDirectoryNamed(normalizedName, error = it) }
                        }
                    }
                    writer.writeEntries()
                } finally {
                    zip.close()
                }
                SystemFileSystem.source(archivePath).buffered().use { it.transferTo(sink) }
            }
        }
    }

    actual override suspend fun read(source: Source, readEntry: suspend (ZipArchiveEntry) -> Unit) = source.use {
        if (!source.request(Int.SIZE_BYTES.toLong())) return@use
        withTemporaryDirectory { directory ->
            val archivePath = Path(directory, "archive.zip")
            // The library reads the central directory through a seekable file.
            SystemFileSystem.sink(archivePath).buffered().use { source.transferTo(it) }
            val zip = checked { RKHZipArchive.openAtPath(archivePath.toString(), writing = false, error = it) }!!
            try {
                while (zip.nextEntry()) {
                    val normalizedName = normalizeName(zip.entryName)
                    var consumed = false
                    readEntry(object : ZipArchiveEntry {
                        override val isDirectory = zip.entryIsDirectory
                        override val name = if (isDirectory) "$normalizedName/" else normalizedName

                        override fun copyTo(sink: Sink): Long {
                            check(!consumed) { "ZIP entry content can only be consumed once: $name" }
                            consumed = true
                            var failure: Throwable? = null
                            var count = 0L
                            try {
                                checked { error ->
                                    zip.readCurrentEntryWithConsumer({ bytes, length ->
                                        try {
                                            if (length > 0) {
                                                sink.write(bytes!!.reinterpret<ByteVar>().readBytes(length.toInt()))
                                            }
                                            count += length
                                            true
                                        } catch (error: Throwable) {
                                            failure = error
                                            false
                                        }
                                    }, error = error)
                                }
                            } catch (error: Throwable) {
                                throw failure ?: error
                            }
                            sink.flush()
                            return count
                        }
                    })
                    if (!consumed) checked { zip.readCurrentEntryWithConsumer({ _, _ -> true }, error = it) }
                }
            } finally {
                zip.close()
            }
        }
    }

    private fun normalizeName(name: String): String = ZipEntryPathPolicy.normalizeOrNull(name)
        ?: throw ZipArchiveException("Unsafe ZIP entry path: $name")

    private inline fun <T> checked(block: (CPointer<ObjCObjectVar<NSError?>>) -> T): T = memScoped {
        val error = alloc<ObjCObjectVar<NSError?>>()
        error.value = null
        val result = block(error.ptr)
        error.value?.let { throw ZipArchiveException(it.localizedDescription) }
        result
    }

    private inline fun <T> withTemporaryDirectory(block: (Path) -> T): T {
        val directory = Path(NSTemporaryDirectory(), "rikkahub-zip-${NSUUID().UUIDString}")
        SystemFileSystem.createDirectories(directory)
        return try {
            block(directory)
        } finally {
            NSFileManager.defaultManager.removeItemAtPath(directory.toString(), null)
        }
    }
}
