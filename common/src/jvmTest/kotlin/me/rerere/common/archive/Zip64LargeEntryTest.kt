package me.rerere.common.archive

import kotlinx.coroutines.runBlocking
import kotlinx.io.Buffer
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.io.readByteArray
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Zip64LargeEntryTest {
    @Test
    fun streamsMoreThanFourGiBInBothDirectionsWithoutWholeEntryAllocation() = runBlocking {
        val size = 0xffff_ffffL + 1025
        for (platformWrites in listOf(true, false)) {
            val input = RepeatedInput(size)
            val bytes = if (platformWrites) {
                Buffer().apply {
                    PlatformZipArchive.create(this) { add("large.bin", input.asSource().buffered()) }
                }.readByteArray()
            } else {
                ByteArrayOutputStream().apply {
                    ZipOutputStream(this).use { zip ->
                        zip.putNextEntry(ZipEntry("large.bin"))
                        input.use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }.toByteArray()
            }
            assertTrue(input.closed)
            assertEquals(size, input.count)
            assertTrue(bytes.size < 8 * 1024 * 1024)
            val output = CountingOutput()
            if (platformWrites) {
                ZipInputStream(bytes.inputStream()).use { zip ->
                    assertEquals("large.bin", zip.nextEntry.name)
                    assertEquals(size, zip.copyTo(output))
                    assertNull(zip.nextEntry)
                }
            } else {
                var entries = 0
                PlatformZipArchive.read(Buffer().apply { write(bytes) }) { entry ->
                    entries++
                    assertEquals("large.bin", entry.name)
                    assertEquals(size, entry.copyTo(output.asSink().buffered()))
                }
                assertEquals(1, entries)
            }
            assertEquals(size, output.count)
            assertEquals(input.crc.value, output.crc.value)
        }
    }

    private class RepeatedInput(private val size: Long) : InputStream() {
        private val block = ByteArray(8192) { 0x5a }
        val crc = CRC32()
        var count = 0L
        var closed = false
        override fun read(): Int = error("Bulk reads expected")
        override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
            if (count == size) return -1
            val n = minOf(length.toLong(), block.size.toLong(), size - count).toInt()
            block.copyInto(bytes, offset, 0, n)
            crc.update(bytes, offset, n)
            count += n
            return n
        }
        override fun close() { closed = true }
    }

    private class CountingOutput : OutputStream() {
        val crc = CRC32()
        var count = 0L
        override fun write(byte: Int) = error("Bulk writes expected")
        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            crc.update(bytes, offset, length)
            count += length
        }
    }
}
