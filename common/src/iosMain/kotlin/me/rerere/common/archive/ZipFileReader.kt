@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package me.rerere.common.archive

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.io.Buffer
import kotlinx.io.files.Path
import kotlinx.io.readByteArray
import swiftPMImport.rikkahub.common.RKHZipArchive

actual class ZipFileReader actual constructor(path: Path) : AutoCloseable {
    private val zip = PlatformZipArchive.checked {
        RKHZipArchive.openAtPath(path.toString(), writing = false, error = it)
    }!!
    private val names = buildList {
        while (zip.nextEntry()) add(zip.entryName)
    }

    actual fun entries(): List<String> = names

    actual fun readEntry(name: String): ByteArray? {
        if (!zip.selectEntryNamed(name)) return null
        val buffer = Buffer()
        PlatformZipArchive.checked { error ->
            zip.readCurrentEntryWithConsumer({ bytes, length ->
                if (length > 0) buffer.write(bytes!!.reinterpret<ByteVar>().readBytes(length.toInt()))
                true
            }, error = error)
        }
        return buffer.readByteArray()
    }

    actual override fun close() = zip.close()
}
