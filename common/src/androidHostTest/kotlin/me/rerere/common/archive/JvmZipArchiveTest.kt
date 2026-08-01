package me.rerere.common.archive

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class JvmZipArchiveTest {
    @Test
    fun `archives empty and unicode entries readable by java`() {
        val output = Buffer()
        JvmZipArchive.create(output) {
            addDirectory("目录")
            addBytes("empty.bin", byteArrayOf())
            addText("目录/天气.txt", "晴天 ☀️")
        }
        val archive = output.readByteArray()

        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(archive)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes()
            }
        }

        assertEquals(setOf("目录/", "empty.bin", "目录/天气.txt"), entries.keys)
        assertArrayEquals(byteArrayOf(), entries.getValue("empty.bin"))
        assertEquals("晴天 ☀️", entries.getValue("目录/天气.txt").decodeToString())
    }

    @Test
    fun `reads java archive and streams entry content`() = runBlocking {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("folder/large.bin"))
            zip.write(ByteArray(256 * 1024) { index -> (index % 251).toByte() })
            zip.closeEntry()
        }

        val entries = linkedMapOf<String, ByteArray>()
        JvmZipArchive.read(Buffer().apply { write(output.toByteArray()) }) { entry ->
            yield()
            entries[entry.name] = entry.readBytes()
        }

        val expected = ByteArray(256 * 1024) { index -> (index % 251).toByte() }
        assertArrayEquals(expected, entries.getValue("folder/large.bin"))
    }
}
