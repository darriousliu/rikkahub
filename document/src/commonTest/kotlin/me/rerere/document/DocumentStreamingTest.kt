package me.rerere.document

import kotlinx.io.Buffer
import kotlinx.io.RawSource
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.io.readString
import me.rerere.common.archive.ZipFileReader
import nl.adaptivity.xmlutil.EventType
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class DocumentStreamingTest {
    @Test
    fun xmlReadsOnlyABoundedPrefixAndDecodesSplitUtf8Characters() {
        val prefix = "\uFEFF<root><t>中文🙂 &amp; <![CDATA[流式]]></t>".encodeToByteArray()
        var consumed = 0
        var closed = false
        val source = object : RawSource {
            override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
                // More data exists, but a streaming parser must not request it before the first TEXT.
                check(consumed < 64 * 1024) { "XML was read eagerly" }
                val count = minOf(byteCount, 3).toInt()
                repeat(count) {
                    sink.writeByte(if (consumed < prefix.size) prefix[consumed] else ' '.code.toByte())
                    consumed++
                }
                return count.toLong()
            }

            override fun close() { closed = true }
        }.buffered()
        source.use {
            val parser = DocumentXmlReader(it)
            while (parser.next() != EventType.TEXT) { }
            assertEquals("中文🙂 & 流式", parser.text)
            assertTrue(consumed < 64 * 1024, "Only a bounded prefix may be loaded")
        }
        assertTrue(closed)
    }

    @Test
    fun openingAndClosingALargeEntryDoesNotReadItsInvalidTail() = withZip(invalidTailZip) { zip ->
        repeat(3) {
            zip.openEntry("large.xml")!!.use { source ->
                assertEquals("<root>", source.readString(6))
            }
        }
    }

    @Test
    fun entryReadFailureIsReportedAndAnotherSourceCanBeOpened() = withZip(invalidTailZip) { zip ->
        assertFails {
            zip.openEntry("large.xml")!!.use { source ->
                val discard = Buffer()
                while (source.readAtMostTo(discard, 8192) >= 0) discard.clear()
            }
        }
        zip.openEntry("large.xml")!!.use { assertEquals('<'.code.toByte(), it.readByte()) }
    }

    @Test
    fun closingArchiveStopsAnUnreadEntryAndClosesItsSource() = withZip(invalidTailZip) { zip ->
        val source = zip.openEntry("large.xml")!!
        zip.close()
        assertFails { source.readByte() }
        source.close()
    }

    @Test
    fun emptyEntryReturnsEndOfStreamAndNextEntryRemainsReadable() {
        val archive = "UEsDBBQAAAAIAO5yL10AAAAAAgAAAAAAAAAJAAAAZW1wdHkueG1sAwBQSwMEFAAAAAgA7nIvXTwQLwQGAAAABAAAAAgAAABuZXh0" +
            "LnhtbMtLrSgBAFBLAQIUAxQAAAAIAO5yL10AAAAAAgAAAAAAAAAJAAAAAAAAAAAAAACAAQAAAABlbXB0eS54bWxQSwECFAMUAAAA" +
            "CADuci9dPBAvBAYAAAAEAAAACAAAAAAAAAAAAAAAgAEpAAAAbmV4dC54bWxQSwUGAAAAAAIAAgBtAAAAVQAAAAAA"
        withZip(archive) { zip ->
            zip.openEntry("empty.xml")!!.use { assertTrue(it.exhausted()) }
            zip.openEntry("next.xml")!!.use { assertEquals("next", it.readString()) }
        }
    }

    @Test
    fun twoEntryStreamsKeepIndependentPositions() = withZip(documentFixtures.getValue("CMP79.pptx").base64) { zip ->
        zip.openEntry("ppt/slides/slide1.xml")!!.use { first ->
            zip.openEntry("ppt/slides/slide2.xml")!!.use { second ->
                assertEquals('<'.code.toByte(), first.readByte())
                assertEquals('<'.code.toByte(), second.readByte())
                assertTrue(first.readString().contains("CMP79 Slide ONE"))
                assertTrue(second.readString().contains("CMP79 Slide TWO"))
            }
        }
    }

    private fun withZip(base64: String, block: (ZipFileReader) -> Unit) {
        val path = Path(SystemTemporaryDirectory, "document-stream-${Uuid.random()}.zip")
        SystemFileSystem.sink(path).buffered().use { it.write(Base64.decode(base64)) }
        try {
            ZipFileReader(path).use(block)
        } finally {
            SystemFileSystem.delete(path)
        }
    }
}
