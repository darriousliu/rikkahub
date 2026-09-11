package me.rerere.common.archive

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.io.Buffer
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.io.readByteArray
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlatformZipArchiveTest {
    private val entries = linkedMapOf(
        "settings.json" to "{\"fixture\":\"迁移前 ZIP\"}".encodeToByteArray(),
        "rikka_hub.db" to byteArrayOf(0, 1, 2, -1),
        "rikka_hub-wal" to "wal".encodeToByteArray(),
        "rikka_hub-shm" to "shm".encodeToByteArray(),
        "skills/空目录/" to byteArrayOf(),
        "upload/中文 😀.txt" to "正文\nsecond line".encodeToByteArray(),
    )

    @Test
    fun readsArchiveCapturedFromPreRollbackImplementation() = runBlocking {
        // Synthetic archive captured from PlatformZipArchive at 03c6ce149, before this rollback.
        assertEntries(entries, readPlatform(resource("migration-before-17a.zip")))
    }

    @Test
    fun readsOriginalJdkWriterStoredAndDeflatedEntries() = runBlocking {
        for (method in listOf(ZipEntry.STORED, ZipEntry.DEFLATED)) {
            assertEntries(entries, readPlatform(writeJdk(entries, method)))
        }
    }

    @Test
    fun outputIsReadableByOriginalJdkStreamAndIndependentCentralDirectoryReader() {
        val before = System.currentTimeMillis() - 2_000
        val bytes = writePlatform(entries)
        assertEntries(entries, readJdk(bytes))
        withZipFile(bytes) { zip ->
            assertEquals(entries.keys.toList(), zip.entries().asSequence().map { it.name }.toList())
            for ((name, content) in entries) {
                val entry = zip.getEntry(name)
                assertEquals(ZipEntry.DEFLATED, entry.method)
                assertEquals(content.size.toLong(), entry.size)
                assertEquals(name.endsWith('/'), entry.isDirectory)
                assertTrue(entry.time in before..System.currentTimeMillis())
                assertEquals(CRC32().apply { update(content) }.value, entry.crc)
                zip.getInputStream(entry).use { assertContentEquals(content, it.readBytes()) }
            }
        }
    }

    @Test
    fun readsPythonForcedZip64LocalHeaderWithSmallPayload() = runBlocking {
        // Python ZipFile.open(force_zip64=True) produced ZIP64 local headers for this small payload.
        assertEntries(mapOf("upload/强制ZIP64.txt" to "ZIP64 中文数据".encodeToByteArray()),
            readPlatform(resource("python-forced-zip64.zip")))
    }

    @Test
    fun archiveWithMoreThan65535EntriesUsesZip64AndIsReadableBothWays() = runBlocking {
        val expected = (0..65_535).associate { "empty/$it" to byteArrayOf() }
        val bytes = writePlatform(expected)
        withZipFile(bytes) { assertEquals(expected.size, it.size()) }
        var count = 0
        PlatformZipArchive.read(Buffer().apply { write(writeJdk(expected)) }) { entry ->
            assertEquals("empty/$count", entry.name)
            assertContentEquals(byteArrayOf(), entry.readBytes())
            count++
        }
        assertEquals(expected.size, count)
    }

    @Test
    fun skippingAnEntryDrainsItBeforeReadingTheNextEntry() = runBlocking {
        val bytes = writeJdk(linkedMapOf("ignored.bin" to ByteArray(1_048_777) { it.toByte() },
            "wanted.txt" to "last".encodeToByteArray()))
        val names = mutableListOf<String>()
        PlatformZipArchive.read(Buffer().apply { write(bytes) }) { entry ->
            names += entry.name
            if (entry.name == "wanted.txt") assertEquals("last", entry.readText())
        }
        assertEquals(listOf("ignored.bin", "wanted.txt"), names)
    }

    @Test
    fun pathGuardRejectsTraversalAbsoluteDrivesAndNulBeforeDispatch() = runBlocking {
        for (name in listOf("../escape", "/absolute", "C:/drive", "x/../escape", "x/\u0000")) {
            var dispatched = false
            assertFailsWith<ZipArchiveException> {
                PlatformZipArchive.read(Buffer().apply { write(writeJdk(mapOf(name to byteArrayOf(1)))) }) {
                    dispatched = true
                }
            }
            assertFalse(dispatched)
            val input = TrackingInput(byteArrayOf(1))
            assertFailsWith<ZipArchiveException> {
                PlatformZipArchive.create(Buffer()) { add(name, input.asSource().buffered()) }
            }
            assertTrue(input.closed)
        }
    }

    @Test
    fun existingPathNormalizationIsPreserved() = runBlocking {
        assertEntries(mapOf("upload/a.txt" to byteArrayOf(1)),
            readPlatform(writeJdk(mapOf("./upload\\a.txt" to byteArrayOf(1)))))
        assertEntries(mapOf("skills/empty/" to byteArrayOf()),
            readJdk(writePlatform(mapOf("./skills/empty/" to byteArrayOf()))))
    }

    @Test
    fun corruptCrcAndTruncatedPayloadHaveOriginalJdkFailureTypes() = runBlocking<Unit> {
        val content = ByteArray(1000) { (it % 127).toByte() }
        val bytes = writeJdk(mapOf("file.bin" to content), ZipEntry.STORED)
        val dataOffset = 30 + "file.bin".length
        val corrupt = bytes.copyOf().apply { this[dataOffset] = (this[dataOffset].toInt() xor 1).toByte() }
        val truncated = bytes.copyOf(dataOffset + 500)
        for (input in listOf(corrupt, truncated)) {
            val original = runCatching { readJdk(input) }.exceptionOrNull()!!
            val current = runCatching { readPlatform(input) }.exceptionOrNull()!!
            assertEquals(original.javaClass, current.javaClass)
            assertEquals(original.message, current.message)
        }
        assertFailsWith<ZipException> {
            PlatformZipArchive.read(Buffer().apply { write(corrupt) }) { /* skipped entries still check CRC */ }
        }
    }

    @Test
    fun noLocalEntriesAndMissingCentralDirectoryKeepOriginalStreamBehavior() = runBlocking {
        for (bytes in listOf(byteArrayOf(), "not a zip".encodeToByteArray(), writeJdk(emptyMap()))) {
            assertEntries(readJdk(bytes), readPlatform(bytes))
        }
        val bytes = writeJdk(mapOf("one" to byteArrayOf(1)))
        val central = bytes.indices.first { index ->
            index + 3 < bytes.size && bytes[index] == 0x50.toByte() && bytes[index + 1] == 0x4b.toByte() &&
                bytes[index + 2] == 1.toByte() && bytes[index + 3] == 2.toByte()
        }
        assertEntries(readJdk(bytes.copyOf(central)), readPlatform(bytes.copyOf(central)))
    }

    @Test
    fun streamsCloseOnSuccessAndCallbackFailureButEntrySinkStaysOpen() = runBlocking {
        val output = TrackingOutput()
        val input = TrackingInput(byteArrayOf(1, 2, 3))
        PlatformZipArchive.create(output.asSink().buffered()) { add("one", input.asSource().buffered()) }
        assertTrue(input.closed)
        assertTrue(output.closed)
        val source = TrackingInput(output.toByteArray())
        val destination = TrackingOutput()
        PlatformZipArchive.read(source.asSource().buffered()) { entry ->
            val sink = destination.asSink().buffered()
            assertEquals(3L, entry.copyTo(sink))
            assertFalse(destination.closed)
            assertContentEquals(byteArrayOf(1, 2, 3), destination.toByteArray())
            assertFailsWith<IllegalStateException> { entry.copyTo(sink) }
        }
        assertTrue(source.closed)
        for (failure in listOf(IOException("handler failed"), CancellationException("cancelled"))) {
            val reading = TrackingInput(output.toByteArray())
            val actual = runCatching {
                PlatformZipArchive.read(reading.asSource().buffered()) { throw failure }
            }.exceptionOrNull()!!
            assertEquals(failure.javaClass.name, actual.javaClass.name)
            assertEquals(failure.message, actual.message)
            assertTrue(reading.closed)
            val writing = TrackingOutput()
            assertEquals(failure, runCatching {
                PlatformZipArchive.create(writing.asSink().buffered()) { throw failure }
            }.exceptionOrNull())
            assertTrue(writing.closed)
        }
    }

    @Test
    fun duplicateEntriesKeepJdkExceptionAndCloseTheRejectedSource() {
        val output = TrackingOutput()
        val rejected = TrackingInput(byteArrayOf(2))
        assertFailsWith<ZipException> {
            PlatformZipArchive.create(output.asSink().buffered()) {
                addBytes("one", byteArrayOf(1))
                add("one", rejected.asSource().buffered())
            }
        }
        assertTrue(rejected.closed)
        assertTrue(output.closed)
    }

    private fun writePlatform(entries: Map<String, ByteArray>) = Buffer().apply {
        PlatformZipArchive.create(this) {
            for ((name, bytes) in entries) {
                if (name.endsWith('/')) addDirectory(name) else addBytes(name, bytes)
            }
        }
    }.readByteArray()

    private fun writeJdk(entries: Map<String, ByteArray>, method: Int = ZipEntry.DEFLATED): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.setComment("独立 JDK fixture")
            for ((name, bytes) in entries) {
                zip.putNextEntry(ZipEntry(name).apply {
                    this.method = method
                    if (method == ZipEntry.STORED) {
                        size = bytes.size.toLong()
                        compressedSize = size
                        crc = CRC32().apply { update(bytes) }.value
                    }
                })
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private suspend fun readPlatform(bytes: ByteArray) = linkedMapOf<String, ByteArray>().apply {
        PlatformZipArchive.read(Buffer().apply { write(bytes) }) { entry -> put(entry.name, entry.readBytes()) }
    }

    private fun readJdk(bytes: ByteArray) = linkedMapOf<String, ByteArray>().apply {
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                put(entry.name, zip.readBytes())
                zip.closeEntry()
            }
        }
    }

    private fun assertEntries(expected: Map<String, ByteArray>, actual: Map<String, ByteArray>) {
        assertEquals(expected.keys.toList(), actual.keys.toList())
        for ((name, bytes) in expected) assertContentEquals(bytes, actual[name], name)
    }

    private fun resource(name: String) = javaClass.getResourceAsStream("/archive/$name")!!.use { it.readBytes() }

    private fun withZipFile(bytes: ByteArray, block: (ZipFile) -> Unit) {
        val file = File.createTempFile("cmp17-zip-", ".zip")
        try {
            file.writeBytes(bytes)
            ZipFile(file).use(block)
        } finally {
            file.delete()
        }
    }

    private class TrackingInput(bytes: ByteArray) : ByteArrayInputStream(bytes) {
        var closed = false
        override fun close() { closed = true }
    }

    private class TrackingOutput : ByteArrayOutputStream() {
        var closed = false
        override fun close() { closed = true }
    }
}
