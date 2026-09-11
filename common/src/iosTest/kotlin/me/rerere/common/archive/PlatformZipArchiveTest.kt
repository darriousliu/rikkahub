@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package me.rerere.common.archive

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.io.Buffer
import kotlinx.io.RawSink
import kotlinx.io.RawSource
import kotlinx.io.buffered
import kotlinx.io.readByteArray
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PlatformZipArchiveTest {
    @Test
    fun readsPreRollbackArchiveAndIndependentPythonZip64() = runBlocking {
        val legacy = readContents(Base64.decode(legacyZip))
        assertEquals(listOf("settings.json", "rikka_hub.db", "rikka_hub-wal", "rikka_hub-shm",
            "skills/空目录/", "upload/中文 😀.txt"), legacy.keys.toList())
        assertContentEquals("正文\nsecond line".encodeToByteArray(), legacy["upload/中文 😀.txt"])
        assertContentEquals(byteArrayOf(0, 1, 2, -1), legacy["rikka_hub.db"])
        val zip64 = readContents(Base64.decode(pythonZip64))
        assertEquals(listOf("upload/强制ZIP64.txt"), zip64.keys.toList())
        assertContentEquals("ZIP64 中文数据".encodeToByteArray(), zip64.values.single())
    }

    @Test
    fun roundTripsUnicodeAndDirectoriesAndHonorsStreamOwnership() = runBlocking {
        val before = temporaryArchives()
        val payload = ByteArray(131_073) { it.toByte() }
        val entrySource = TrackingSource(Buffer().apply { write(payload) })
        val archiveSink = TrackingSink()
        PlatformZipArchive.create(archiveSink.buffered()) {
            add("upload/中文 😀.txt", entrySource.buffered())
            addDirectory("skills/empty")
        }
        assertTrue(entrySource.closed)
        assertTrue(archiveSink.closed)
        val archiveSource = TrackingSource(archiveSink.output)
        val names = mutableListOf<String>()
        PlatformZipArchive.read(archiveSource.buffered()) { entry ->
            names += entry.name
            assertEquals(entry.name.endsWith('/'), entry.isDirectory)
            val destination = TrackingSink()
            val sink = destination.buffered()
            val expected = if (entry.isDirectory) byteArrayOf() else payload
            assertEquals(expected.size.toLong(), entry.copyTo(sink))
            assertContentEquals(expected, destination.output.readByteArray())
            assertFalse(destination.closed)
            assertFailsWith<IllegalStateException> { entry.copyTo(sink) }
        }
        assertEquals(listOf("upload/中文 😀.txt", "skills/empty/"), names)
        assertTrue(archiveSource.closed)
        assertEquals(before, temporaryArchives())
    }

    @Test
    fun rejectsUnsafePathsAndCorruptCrcEvenWhenEntryIsSkipped() = runBlocking {
        val before = temporaryArchives()
        var called = false
        assertFailsWith<ZipArchiveException> {
            PlatformZipArchive.read(Buffer().apply { write(Base64.decode(unsafeZip)) }) { called = true }
        }
        assertFalse(called)
        val rejected = TrackingSource(Buffer().apply { writeByte(1) })
        assertFailsWith<ZipArchiveException> {
            PlatformZipArchive.create(Buffer()) { add("../unsafe", rejected.buffered()) }
        }
        assertTrue(rejected.closed)
        val corrupt = Base64.decode(storedZip).apply { this[30 + "crc.txt".length] = 0 }
        for (consume in listOf(false, true)) {
            assertFailsWith<ZipArchiveException> {
                PlatformZipArchive.read(Buffer().apply { write(corrupt) }) { if (consume) it.readBytes() }
            }
        }
        assertEquals(before, temporaryArchives())
    }

    @Test
    fun rejectsDuplicateEntriesAndKeepsEmptyInputBehavior() = runBlocking {
        val before = temporaryArchives()
        for (directory in listOf(false, true)) {
            assertFailsWith<ZipArchiveException> {
                PlatformZipArchive.create(Buffer()) {
                    if (directory) {
                        addDirectory("same")
                        addDirectory("same/")
                    } else {
                        addText("same", "first")
                        addText("same", "second")
                    }
                }
            }
        }
        val emptyArchive = Buffer().apply { PlatformZipArchive.create(this) {} }.readByteArray()
        for (bytes in listOf(byteArrayOf(), byteArrayOf(1, 2, 3), emptyArchive)) {
            val input = TrackingSource(Buffer().apply { write(bytes) })
            PlatformZipArchive.read(input.buffered()) { error("Empty archive must have no entries") }
            assertTrue(input.closed)
        }
        assertEquals(before, temporaryArchives())
    }

    @Test
    fun propagatesCallbackAndIoFailuresAndRemovesTemporaryFiles() = runBlocking {
        val before = temporaryArchives()
        val archive = Buffer().apply {
            PlatformZipArchive.create(this) { addBytes("one.bin", ByteArray(65_537)) }
        }.readByteArray()
        for (failure in listOf(IllegalStateException("callback failed"), CancellationException("cancelled"))) {
            val output = TrackingSink()
            assertSame(failure, runCatching {
                PlatformZipArchive.create(output.buffered()) { throw failure }
            }.exceptionOrNull())
            assertTrue(output.closed)
            val input = TrackingSource(Buffer().apply { write(archive) })
            assertSame(failure, runCatching {
                PlatformZipArchive.read(input.buffered()) { throw failure }
            }.exceptionOrNull())
            assertTrue(input.closed)
        }
        val failure = IllegalStateException("stream failed")
        val brokenInput = TrackingSource(Buffer(), failure)
        assertSame(failure, runCatching {
            PlatformZipArchive.create(Buffer()) { add("broken", brokenInput.buffered()) }
        }.exceptionOrNull())
        assertTrue(brokenInput.closed)
        val input = TrackingSource(Buffer().apply { write(archive) })
        val brokenOutput = TrackingSink(failure)
        assertSame(failure, runCatching {
            PlatformZipArchive.read(input.buffered()) { it.copyTo(brokenOutput.buffered()) }
        }.exceptionOrNull())
        assertTrue(input.closed)
        assertFalse(brokenOutput.closed)
        assertEquals(before, temporaryArchives())
    }

    private suspend fun readContents(bytes: ByteArray) = linkedMapOf<String, ByteArray>().apply {
        PlatformZipArchive.read(Buffer().apply { write(bytes) }) { put(it.name, it.readBytes()) }
    }

    private fun temporaryArchives() = NSFileManager.defaultManager
        .contentsOfDirectoryAtPath(NSTemporaryDirectory(), null).orEmpty()
        .filterIsInstance<String>().filter { it.startsWith("rikkahub-zip-") }.toSet()

    private class TrackingSource(private val input: Buffer, private val failure: Throwable? = null) : RawSource {
        var closed = false
        override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
            failure?.let { throw it }
            return input.readAtMostTo(sink, byteCount)
        }
        override fun close() { closed = true }
    }

    private class TrackingSink(private val failure: Throwable? = null) : RawSink {
        val output = Buffer()
        var closed = false
        override fun write(source: Buffer, byteCount: Long) {
            failure?.let { throw it }
            output.write(source, byteCount)
        }
        override fun flush() = Unit
        override fun close() { closed = true }
    }

    // Same synthetic pre-17A and Python force_zip64 fixtures used by the Java adapter tests.
    private val legacyZip =
        "UEsDBBQACAgIAAAAAAAAAAAAAAAAAAAAAAANAAAAc2V0dGluZ3MuanNvbqtWSsusKCktSlWyUnqxv/H58t1PO3sVojwDlGoBUEsH" +
        "CC+4fiwdAAAAGwAAAFBLAwQUAAgICAAAAAAAAAAAAAAAAAAAAAAADAAAAHJpa2thX2h1Yi5kYmNgZPoPAFBLBwgkOLI/BgAAAAQA" +
        "AABQSwMEFAAICAgAAAAAAAAAAAAAAAAAAAAAAA0AAAByaWtrYV9odWItd2FsK0/MAQBQSwcIUtAdlgUAAAADAAAAUEsDBBQACAgI" +
        "AAAAAAAAAAAAAAAAAAAAAAANAAAAcmlra2FfaHViLXNobSvOyAUAUEsHCFHz0TcFAAAAAwAAAFBLAwQUAAAIAAAAAAAAAAAAAAAA" +
        "AAAAAAAAEQAAAHNraWxscy/nqbrnm67lvZUvUEsDBBQACAgIAAAAAAAAAAAAAAAAAAAAAAAWAAAAdXBsb2FkL+S4reaWhyDwn5iA" +
        "LnR4dHu2dvGzae1cxanJ+XkpCjmZeakAUEsHCHTdZIUUAAAAEgAAAFBLAQIUABQACAgIAAAAAAAvuH4sHQAAABsAAAANAAAAAAAA" +
        "AAAAAAAAAAAAAABzZXR0aW5ncy5qc29uUEsBAhQAFAAICAgAAAAAACQ4sj8GAAAABAAAAAwAAAAAAAAAAAAAAAAAWAAAAHJpa2th" +
        "X2h1Yi5kYlBLAQIUABQACAgIAAAAAABS0B2WBQAAAAMAAAANAAAAAAAAAAAAAAAAAJgAAAByaWtrYV9odWItd2FsUEsBAhQAFAAI" +
        "CAgAAAAAAFHz0TcFAAAAAwAAAA0AAAAAAAAAAAAAAAAA2AAAAHJpa2thX2h1Yi1zaG1QSwECFAAUAAAIAAAAAAAAAAAAAAAAAAAA" +
        "AAAAEQAAAAAAAAAAABAAAAAYAQAAc2tpbGxzL+epuuebruW9lS9QSwECFAAUAAgICAAAAAAAdN1khRQAAAASAAAAFgAAAAAAAAAA" +
        "AAAAAABHAQAAdXBsb2FkL+S4reaWhyDwn5iALnR4dFBLBQYAAAAABgAGAG4BAACfAQAAAAA="

    private val pythonZip64 =
        "UEsDBC0AAAgIAAAAK11Ll+oz//////////8WABQAdXBsb2FkL+W8uuWItlpJUDY0LnR4dAEAEAASAAAAAAAAABUAAAAAAAAAi/IM" +
        "MDNReLJj7bNp7c+mbnjWuw4AUEsBAi0DLQAACAgAAAArXUuX6jMVAAAAEgAAABYAAAAAAAAAAAAAAIABAAAAAHVwbG9hZC/lvLrl" +
        "iLZaSVA2NC50eHRQSwUGAAAAAAEAAQBEAAAAXQAAAAAA"

    // Independent Python zipfile STORED fixtures with fixed 2026-09-12 timestamps.
    private val storedZip =
        "UEsDBBQAAAAAAAAALF10yMAbEAAAABAAAAAHAAAAY3JjLnR4dENSQyBmaXh0dXJlIGJvZHlQSwECFAMUAAAAAAAAACxddMjAGxAA" +
        "AAAQAAAABwAAAAAAAAAAAAAAgAEAAAAAY3JjLnR4dFBLBQYAAAAAAQABADUAAAA1AAAAAAA="

    private val unsafeZip =
        "UEsDBBQAAAAAAAAALF1uA8/yBgAAAAYAAAAJAAAALi4vdW5zYWZldW5zYWZlUEsBAhQDFAAAAAAAAAAsXW4Dz/IGAAAABgAAAAkA" +
        "AAAAAAAAAAAAAIABAAAAAC4uL3Vuc2FmZVBLBQYAAAAAAQABADcAAAAtAAAAAAA="
}
