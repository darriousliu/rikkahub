package me.rerere.document

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.toKotlinxIoPath
import kotlinx.io.buffered
import kotlinx.io.readByteArray
import kotlinx.io.readString
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import me.rerere.common.archive.ZipFileReader
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class DocumentParserTest {
    @Test
    fun docxMatchesOriginalParagraphFormattingListsAndTable() = checkOriginal("CMP79.docx")

    @Test
    fun pptxMatchesOriginalSlideOrderTablesAndNotes() = checkOriginal("CMP79.pptx")

    @Test
    fun epubMatchesOriginalSpineOrderAndXhtmlFormatting() = checkOriginal("CMP79.epub")

    @Test
    fun missingPartsKeepOriginalMessages() {
        listOf("empty.docx", "empty.pptx", "empty.epub", "missing-opf.epub").forEach(::checkOriginal)
    }

    @Test
    fun malformedXmlKeepsOriginalErrorResult() = withFixture("malformed.docx") { file ->
        assertTrue(DocxParser.parse(file).startsWith("Error parsing document XML:"))
    }

    @Test
    fun zipEntriesCanBeReadRepeatedlyInAnyOrderWithoutExtractingOtherFiles() = withFixture("CMP79.pptx") { file ->
        ZipFileReader(file.toKotlinxIoPath()).use { zip ->
            assertEquals(listOf("ppt/slides/slide2.xml", "ppt/slides/slide1.xml", "ppt/notesSlides/notesSlide1.xml"),
                zip.entries())
            val first = zip.openEntry("ppt/slides/slide1.xml")!!.use { it.readByteArray() }
            assertTrue(zip.openEntry("ppt/slides/slide2.xml")!!.use { it.readString() }.contains("CMP79 Slide TWO"))
            assertNull(zip.openEntry("missing.xml"))
            assertContentEquals(first, zip.openEntry("ppt/slides/slide1.xml")!!.use { it.readByteArray() })
        }
    }

    private fun checkOriginal(name: String) = withFixture(name) { file ->
        val actual = when (name.substringAfterLast('.')) {
            "docx" -> DocxParser.parse(file)
            "pptx" -> PptxParser.parse(file)
            else -> EpubParser.parse(file)
        }
        assertEquals(documentFixtures.getValue(name).expected, actual, name)
    }

    private fun withFixture(name: String, block: (PlatformFile) -> Unit) {
        val path = Path(SystemTemporaryDirectory, "document-${Uuid.random()}-$name")
        SystemFileSystem.sink(path).buffered().use {
            it.write(Base64.decode(documentFixtures.getValue(name).base64))
        }
        try {
            block(PlatformFile(path.toString()))
        } finally {
            SystemFileSystem.delete(path)
        }
    }
}
