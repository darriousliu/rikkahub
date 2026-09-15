package me.rerere.document

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.toKotlinxIoPath
import kotlinx.io.Source
import me.rerere.common.archive.ZipFileReader
import nl.adaptivity.xmlutil.EventType

private data class ListInfo(
    val level: Int,
    val isNumbered: Boolean,
    val number: Int
)

private data class ParagraphProperties(
    val listInfo: ListInfo?,
    val headingLevel: Int
)

object DocxParser {
    fun parse(file: PlatformFile): String {
        return try {
            ZipFileReader(file.toKotlinxIoPath()).use { zip ->
                val document = zip.openEntry("word/document.xml")
                    ?: return "Unable to find document content in DOCX file"
                document.use { parseDocumentXml(it) }
            }
        } catch (e: Exception) {
            "Error parsing DOCX file: ${e.message}"
        }
    }

    private fun parseDocumentXml(inputStream: Source): String {
        return try {
            val parser = DocumentXmlReader(inputStream)

            val result = StringBuilder()
            var inBody = false

            while (parser.eventType != EventType.END_DOCUMENT) {
                when (parser.eventType) {
                    EventType.START_ELEMENT -> {
                        when (parser.name) {
                            "body" -> inBody = true
                            "p" -> if (inBody) processParagraph(parser, result)
                            "tbl" -> if (inBody) processTable(parser, result)
                        }
                    }
                    EventType.END_ELEMENT -> {
                        if (parser.name == "body") inBody = false
                    }
                    else -> Unit
                }
                parser.next()
            }

            result.toString().trim()
        } catch (e: Exception) {
            "Error parsing document XML: ${e.message}"
        }
    }

    private fun processParagraph(parser: DocumentXmlReader, result: StringBuilder) {
        val paragraphStartDepth = parser.depth
        val paragraphContent = StringBuilder()
        var listInfo: ListInfo? = null
        var headingLevel = 0

        while (parser.next() != EventType.END_DOCUMENT) {
            when (parser.eventType) {
                EventType.START_ELEMENT -> {
                    when (parser.name) {
                        "r" -> extractRunText(parser, paragraphContent)
                        "pPr" -> {
                            val props = extractParagraphProperties(parser)
                            listInfo = props.listInfo
                            headingLevel = props.headingLevel
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

        val paragraphText = paragraphContent.toString().trim()
        if (paragraphText.isNotBlank()) {
            when {
                listInfo != null -> {
                    val indent = "  ".repeat(listInfo.level)
                    val marker = if (listInfo.isNumbered) "${listInfo.number}. " else "- "
                    result.append("$indent$marker$paragraphText\n")
                }
                headingLevel > 0 -> {
                    val headingPrefix = "#".repeat(headingLevel)
                    result.append("$headingPrefix $paragraphText\n\n")
                }
                else -> {
                    result.append("$paragraphText\n\n")
                }
            }
        }
    }

    private fun extractRunText(parser: DocumentXmlReader, result: StringBuilder) {
        val runStartDepth = parser.depth
        var isBold = false
        var isItalic = false

        while (parser.next() != EventType.END_DOCUMENT) {
            when (parser.eventType) {
                EventType.START_ELEMENT -> {
                    when (parser.name) {
                        "rPr" -> {
                            val formatting = extractFormatting(parser)
                            isBold = formatting.first
                            isItalic = formatting.second
                        }
                        "t" -> {
                            parser.next()
                            if (parser.eventType == EventType.TEXT) {
                                var text = parser.text ?: ""

                                // Apply markdown formatting
                                text = when {
                                    isBold && isItalic -> "***$text***"
                                    isBold -> "**$text**"
                                    isItalic -> "*$text*"
                                    else -> text
                                }

                                result.append(text)
                            }
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

    private fun extractFormatting(parser: DocumentXmlReader): Pair<Boolean, Boolean> {
        val rPrStartDepth = parser.depth
        var isBold = false
        var isItalic = false

        while (parser.next() != EventType.END_DOCUMENT) {
            when (parser.eventType) {
                EventType.START_ELEMENT -> {
                    when (parser.name) {
                        "b" -> isBold = true
                        "i" -> isItalic = true
                    }
                }
                EventType.END_ELEMENT -> {
                    if (parser.name == "rPr" && parser.depth == rPrStartDepth) {
                        break
                    }
                }
                else -> Unit
            }
        }

        return Pair(isBold, isItalic)
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

            // Add table rows
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
        }
        result.append("\n")
    }

    private fun extractTableRow(parser: DocumentXmlReader): List<String> {
        val rowStartDepth = parser.depth
        val cells = mutableListOf<String>()

        while (parser.next() != EventType.END_DOCUMENT) {
            when (parser.eventType) {
                EventType.START_ELEMENT -> {
                    if (parser.name == "tc") {
                        val cellText = extractCellText(parser)
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

    private fun extractCellText(parser: DocumentXmlReader): String {
        val cellStartDepth = parser.depth
        val result = StringBuilder()

        while (parser.next() != EventType.END_DOCUMENT) {
            when (parser.eventType) {
                EventType.START_ELEMENT -> {
                    if (parser.name == "p") {
                        val paragraphText = extractCellParagraphText(parser)
                        if (paragraphText.isNotBlank()) {
                            if (result.isNotEmpty()) {
                                result.append(" ")
                            }
                            result.append(paragraphText)
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

    private fun extractCellParagraphText(parser: DocumentXmlReader): String {
        val paragraphStartDepth = parser.depth
        val result = StringBuilder()

        while (parser.next() != EventType.END_DOCUMENT) {
            when (parser.eventType) {
                EventType.START_ELEMENT -> {
                    if (parser.name == "r") {
                        extractRunText(parser, result)
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

        return result.toString().trim()
    }

    private fun extractParagraphProperties(parser: DocumentXmlReader): ParagraphProperties {
        val pPrStartDepth = parser.depth
        var listLevel = 0
        var isNumbered = false
        var headingLevel = 0

        while (parser.next() != EventType.END_DOCUMENT) {
            when (parser.eventType) {
                EventType.START_ELEMENT -> {
                    when (parser.name) {
                        "pStyle" -> {
                            val styleVal = parser.getAttributeValue(null, "val")
                            if (styleVal?.startsWith("Heading") == true || styleVal?.startsWith("heading") == true) {
                                headingLevel = styleVal.lastOrNull()?.digitToIntOrNull() ?: 1
                            }
                        }
                        "numPr" -> {
                            val numPrStartDepth = parser.depth
                            while (parser.next() != EventType.END_DOCUMENT) {
                                when (parser.eventType) {
                                    EventType.START_ELEMENT -> when (parser.name) {
                                        "ilvl" -> listLevel = parser.getAttributeValue(null, "val")?.toIntOrNull() ?: 0
                                        "numId" -> isNumbered = parser.getAttributeValue(null, "val") != null
                                    }
                                    EventType.END_ELEMENT -> {
                                        if (parser.name == "numPr" && parser.depth == numPrStartDepth) break
                                    }
                                    else -> Unit
                                }
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

        val listInfo = if (listLevel > 0 || isNumbered) {
            ListInfo(level = listLevel, isNumbered = isNumbered, number = 1)
        } else null

        return ParagraphProperties(listInfo = listInfo, headingLevel = headingLevel)
    }
}
