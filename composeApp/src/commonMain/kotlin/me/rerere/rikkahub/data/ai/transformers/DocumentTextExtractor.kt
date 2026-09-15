package me.rerere.rikkahub.data.ai.transformers

import io.github.vinceglb.filekit.PlatformFile
import me.rerere.document.DocxParser
import me.rerere.document.EpubParser
import me.rerere.document.PptxParser

/**
 * Extracts text from binary document formats such as PDF or DOCX.
 *
 * Returning null means this platform ships no parser for [mime]; the caller then falls back to
 * reading the file as plain text, or reports the format as unsupported.
 */
fun interface DocumentTextExtractor {
    suspend fun extract(file: PlatformFile, mime: String): String?
}

object OfficeDocumentTextExtractor : DocumentTextExtractor {
    override suspend fun extract(file: PlatformFile, mime: String): String? = when (mime) {
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> DocxParser.parse(file)
        "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> PptxParser.parse(file)
        "application/epub+zip" -> EpubParser.parse(file)
        else -> null
    }
}
