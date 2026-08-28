package me.rerere.rikkahub.data.ai.transformers

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.absolutePath
import me.rerere.document.DocxParser
import me.rerere.document.EpubParser
import me.rerere.document.PdfParser
import me.rerere.document.PptxParser
import java.io.File

/** Backed by the Android-only `:document` module. */
object AndroidDocumentTextExtractor : DocumentTextExtractor {
    override suspend fun extract(file: PlatformFile, mime: String): String? {
        val target = File(file.absolutePath())
        return when (mime) {
            "application/pdf" -> PdfParser.parserPdf(target)
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> DocxParser.parse(target)
            "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> PptxParser.parse(target)
            "application/epub+zip" -> EpubParser.parse(target)
            else -> null
        }
    }
}
