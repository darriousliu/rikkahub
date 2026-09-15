@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package me.rerere.common.archive

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.io.Buffer
import kotlinx.io.RawSource
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.files.Path
import swiftPMImport.rikkahub.common.RKHZipArchive

actual class ZipFileReader actual constructor(path: Path) : AutoCloseable {
    private val zip = PlatformZipArchive.checked {
        RKHZipArchive.openAtPath(path.toString(), writing = false, error = it)
    }!!
    private val names = buildList {
        while (zip.nextEntry()) add(zip.entryName)
    }
    private val sources = mutableSetOf<RawSource>()

    actual fun entries(): List<String> = names

    actual fun openEntry(name: String): Source? {
        val entry = PlatformZipArchive.checked { zip.openEntryNamed(name, error = it) } ?: return null
        val source = object : RawSource {
            private val bytes = ByteArray(8192)

            override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
                require(byteCount >= 0)
                if (byteCount == 0L) return 0
                val count = bytes.usePinned { pinned ->
                    PlatformZipArchive.checked { error ->
                        entry.readInto(pinned.addressOf(0), count = minOf(byteCount, bytes.size.toLong()), error = error)
                    }
                }
                if (count > 0) sink.write(bytes, 0, count.toInt())
                return count
            }

            override fun close() {
                entry.close()
                sources.remove(this)
            }
        }
        sources.add(source)
        return source.buffered()
    }

    actual override fun close() {
        sources.toList().forEach { it.close() }
        zip.close()
    }
}
