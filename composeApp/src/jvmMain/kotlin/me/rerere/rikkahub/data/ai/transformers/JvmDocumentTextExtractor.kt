package me.rerere.rikkahub.data.ai.transformers

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.absolutePath
import me.rerere.document.PdfParser
import java.io.File

object JvmDocumentTextExtractor : DocumentTextExtractor {
    override suspend fun extract(file: PlatformFile, mime: String): String? = when (mime) {
        "application/pdf" -> PdfParser.parserPdf(File(file.absolutePath()))
        else -> OfficeDocumentTextExtractor.extract(file, mime)
    }
}
