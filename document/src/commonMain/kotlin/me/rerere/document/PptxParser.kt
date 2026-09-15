package me.rerere.document

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.toKotlinxIoPath
import me.rerere.common.archive.ZipFileReader
import nl.adaptivity.xmlutil.EventType

private data class SlideContent(
    val slideNumber: Int,
    val content: String,
    val notes: String = ""
)

object PptxParser {
    fun parse(file: PlatformFile): String {
        return try {
            ZipFileReader(file.toKotlinxIoPath()).use { zipFile ->
                val slides = mutableListOf<SlideContent>()

                // Find all slide XML files and sort them by number
                val slideEntries = zipFile.entries()
                    .filter { it.matches(Regex("ppt/slides/slide\\d+\\.xml")) }
                    .sortedBy { entry ->
                        entry.substringAfter("slide").substringBefore(".xml").toIntOrNull() ?: 0
                    }

                if (slideEntries.isEmpty()) {
                    return "No slides found in PPTX file"
                }

                // Parse each slide
                slideEntries.forEachIndexed { index, entry ->
                    val slideNumber = index + 1
                    val slideContent = parseSlideXml(zipFile.readEntry(entry)!!.decodeToString())

                    // Try to get notes for this slide
                    val notesEntry = zipFile.readEntry("ppt/notesSlides/notesSlide${slideNumber}.xml")
                    val notes = if (notesEntry != null) {
                        parseNotesXml(notesEntry.decodeToString())
                    } else ""

                    slides.add(SlideContent(slideNumber, slideContent, notes))
                }

                // Format output
                formatOutput(slides)
            }
        } catch (e: Exception) {
            "Error parsing PPTX file: ${e.message}"
        }
    }

    private fun formatOutput(slides: List<SlideContent>): String {
        val result = StringBuilder()

        slides.forEach { slide ->
            result.append("## Slide ${slide.slideNumber}\n\n")
            result.append(slide.content)

            if (slide.notes.isNotBlank()) {
                result.append("\n### Speaker Notes\n\n")
                result.append(slide.notes)
            }

            result.append("\n")
        }

        return result.toString().trim()
    }

    private fun parseSlideXml(xml: String): String {
        return try {
            val parser = DocumentXmlReader(xml)

            val result = StringBuilder()

            while (parser.eventType != EventType.END_DOCUMENT) {
                when (parser.eventType) {
                    EventType.START_ELEMENT -> {
                        when (parser.name) {
                            "sp" -> processShape(parser, result)  // Text box/shape
                            "graphicFrame" -> processGraphicFrame(parser, result)  // Table
                        }
                    }
                    else -> Unit
                }
                parser.next()
            }

            result.toString()
        } catch (e: Exception) {
            "Error parsing slide XML: ${e.message}\n"
        }
    }

    private fun processShape(parser: DocumentXmlReader, result: StringBuilder) {
        val shapeStartDepth = parser.depth
        val textContent = StringBuilder()
        var hasBullet = false
        var bulletLevel = 0
        var isNumbered = false

        while (parser.next() != EventType.END_DOCUMENT) {
            when (parser.eventType) {
                EventType.START_ELEMENT -> {
                    when (parser.name) {
                        "p" -> {
                            // Start of paragraph - check for bullet/numbering
                            val paragraphInfo = processParagraph(parser, textContent)
                            hasBullet = paragraphInfo.first
                            bulletLevel = paragraphInfo.second
                            isNumbered = paragraphInfo.third
                        }
                    }
                }

                EventType.END_ELEMENT -> {
                    if (parser.name == "sp" && parser.depth == shapeStartDepth) {
                        break
                    }
                }
                else -> Unit
            }
        }

        val text = textContent.toString().trim()
        if (text.isNotBlank()) {
            result.append(text)
            result.append("\n\n")
        }
    }

    private fun processParagraph(parser: DocumentXmlReader, result: StringBuilder): Triple<Boolean, Int, Boolean> {
        val paragraphStartDepth = parser.depth
        val paragraphText = StringBuilder()
        var hasBullet = false
        var bulletLevel = 0
        var isNumbered = false

        while (parser.next() != EventType.END_DOCUMENT) {
            when (parser.eventType) {
                EventType.START_ELEMENT -> {
                    when (parser.name) {
                        "pPr" -> {
                            // Paragraph properties - check for bullets
                            val bulletInfo = extractBulletInfo(parser)
                            hasBullet = bulletInfo.first
                            bulletLevel = bulletInfo.second
                            isNumbered = bulletInfo.third
                        }

                        "r" -> {
                            // Text run
                            extractTextRun(parser, paragraphText)
                        }
                    }
                }

                EventType.END_ELEMENT -> {
                    if (parser.name == "p" && parser.depth == paragraphStartDepth) {
                        break
                    }
                }
                else -> Unit
            }
        }

        val text = paragraphText.toString().trim()
        if (text.isNotBlank()) {
            if (hasBullet) {
                val indent = "  ".repeat(bulletLevel)
                val marker = if (isNumbered) "1. " else "- "
                result.append("$indent$marker$text\n")
            } else {
                result.append("$text\n")
            }
        }

        return Triple(hasBullet, bulletLevel, isNumbered)
    }

    private fun extractBulletInfo(parser: DocumentXmlReader): Triple<Boolean, Int, Boolean> {
        val pPrStartDepth = parser.depth
        var hasBullet = false
        var level = 0
        var isNumbered = false

        while (parser.next() != EventType.END_DOCUMENT) {
            when (parser.eventType) {
                EventType.START_ELEMENT -> {
                    when (parser.name) {
                        "buChar" -> {
                            hasBullet = true
                            isNumbered = false
                        }

                        "buAutoNum" -> {
                            hasBullet = true
                            isNumbered = true
                        }

                        "lvl" -> {
                            parser.getAttributeValue(null, "val")?.let {
                                level = it.toIntOrNull() ?: 0
                            }
                        }
                    }
                }

                EventType.END_ELEMENT -> {
                    if (parser.name == "pPr" && parser.depth == pPrStartDepth) {
                        break
                    }
                }
                else -> Unit
            }
        }

        return Triple(hasBullet, level, isNumbered)
    }

    private fun extractTextRun(parser: DocumentXmlReader, result: StringBuilder) {
        val runStartDepth = parser.depth

        while (parser.next() != EventType.END_DOCUMENT) {
            when (parser.eventType) {
                EventType.START_ELEMENT -> {
                    if (parser.name == "t") {
                        parser.next()
                        if (parser.eventType == EventType.TEXT) {
                            result.append(parser.text ?: "")
                        }
                    }
                }

                EventType.END_ELEMENT -> {
                    if (parser.name == "r" && parser.depth == runStartDepth) {
                        break
                    }
                }
                else -> Unit
            }
        }
    }

    private fun processGraphicFrame(parser: DocumentXmlReader, result: StringBuilder) {
        val frameStartDepth = parser.depth

        while (parser.next() != EventType.END_DOCUMENT) {
            when (parser.eventType) {
                EventType.START_ELEMENT -> {
                    if (parser.name == "tbl") {
                        processTable(parser, result)
                    }
                }

                EventType.END_ELEMENT -> {
                    if (parser.name == "graphicFrame" && parser.depth == frameStartDepth) {
                        break
                    }
                }
                else -> Unit
            }
        }
    }

    private fun processTable(parser: DocumentXmlReader, result: StringBuilder) {
        val tableStartDepth = parser.depth
        val rows = mutableListOf<List<String>>()

        while (parser.next() != EventType.END_DOCUMENT) {
            when (parser.eventType) {
                EventType.START_ELEMENT -> {
                    if (parser.name == "tr") {
                        val cells = extractTableRow(parser)
                        if (cells.isNotEmpty()) {
                            rows.add(cells)
                        }
                    }
                }

                EventType.END_ELEMENT -> {
                    if (parser.name == "tbl" && parser.depth == tableStartDepth) {
                        break
                    }
                }
                else -> Unit
            }
        }

        // Convert to markdown table
        if (rows.isNotEmpty()) {
            val maxCols = rows.maxOfOrNull { it.size } ?: 0

            for ((index, row) in rows.withIndex()) {
                result.append("| ")
                for (colIndex in 0 until maxCols) {
                    val cellContent = if (colIndex < row.size) row[colIndex] else ""
                    result.append("$cellContent | ")
                }
                result.append("\n")

                // Add separator after first row (header)
                if (index == 0) {
                    result.append("| ")
                    repeat(maxCols) {
                        result.append("--- | ")
                    }
                    result.append("\n")
                }
            }
            result.append("\n")
        }
    }

    private fun extractTableRow(parser: DocumentXmlReader): List<String> {
        val rowStartDepth = parser.depth
        val cells = mutableListOf<String>()

        while (parser.next() != EventType.END_DOCUMENT) {
            when (parser.eventType) {
                EventType.START_ELEMENT -> {
                    if (parser.name == "tc") {
                        val cellText = extractTableCell(parser)
                        cells.add(cellText)
                    }
                }

                EventType.END_ELEMENT -> {
                    if (parser.name == "tr" && parser.depth == rowStartDepth) {
                        break
                    }
                }
                else -> Unit
            }
        }

        return cells
    }

    private fun extractTableCell(parser: DocumentXmlReader): String {
        val cellStartDepth = parser.depth
        val result = StringBuilder()

        while (parser.next() != EventType.END_DOCUMENT) {
            when (parser.eventType) {
                EventType.START_ELEMENT -> {
                    if (parser.name == "t") {
                        parser.next()
                        if (parser.eventType == EventType.TEXT) {
                            if (result.isNotEmpty()) {
                                result.append(" ")
                            }
                            result.append(parser.text ?: "")
                        }
                    }
                }

                EventType.END_ELEMENT -> {
                    if (parser.name == "tc" && parser.depth == cellStartDepth) {
                        break
                    }
                }
                else -> Unit
            }
        }

        return result.toString().trim()
    }

    private fun parseNotesXml(xml: String): String {
        return try {
            val parser = DocumentXmlReader(xml)

            val result = StringBuilder()
            var inNotesShape = false

            while (parser.eventType != EventType.END_DOCUMENT) {
                when (parser.eventType) {
                    EventType.START_ELEMENT -> {
                        when (parser.name) {
                            "sp" -> {
                                // Check if this is a notes text shape (not the slide preview)
                                inNotesShape = isNotesTextShape(parser)
                                if (inNotesShape) {
                                    extractShapeText(parser, result)
                                }
                            }
                        }
                    }
                    else -> Unit
                }
                parser.next()
            }

            result.toString().trim()
        } catch (e: Exception) {
            ""
        }
    }

    private fun isNotesTextShape(parser: DocumentXmlReader): Boolean {
        // Notes text typically has ph type="body"
        val currentDepth = parser.depth
        val originalPosition = parser

        while (parser.next() != EventType.END_DOCUMENT) {
            when (parser.eventType) {
                EventType.START_ELEMENT -> {
                    if (parser.name == "ph") {
                        val type = parser.getAttributeValue(null, "type")
                        return type == "body"
                    }
                }

                EventType.END_ELEMENT -> {
                    if (parser.depth <= currentDepth) {
                        return false
                    }
                }
                else -> Unit
            }
        }
        return false
    }

    private fun extractShapeText(parser: DocumentXmlReader, result: StringBuilder) {
        val shapeStartDepth = parser.depth

        while (parser.next() != EventType.END_DOCUMENT) {
            when (parser.eventType) {
                EventType.START_ELEMENT -> {
                    if (parser.name == "t") {
                        parser.next()
                        if (parser.eventType == EventType.TEXT) {
                            result.append(parser.text ?: "")
                        }
                    }
                }

                EventType.END_ELEMENT -> {
                    if (parser.name == "sp" && parser.depth == shapeStartDepth) {
                        break
                    }
                    if (parser.name == "p") {
                        result.append("\n")
                    }
                }
                else -> Unit
            }
        }
    }
}
