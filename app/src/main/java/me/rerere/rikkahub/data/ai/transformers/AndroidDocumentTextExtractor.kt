package me.rerere.rikkahub.data.ai.transformers

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.absolutePath
import me.rerere.document.PdfParser
import java.io.File

/** Office formats are shared; PDF still uses the original Android MuPDF runtime. */
object AndroidDocumentTextExtractor : DocumentTextExtractor {
    override suspend fun extract(file: PlatformFile, mime: String): String? {
        val target = File(file.absolutePath())
        return when (mime) {
            "application/pdf" -> PdfParser.parserPdf(target)
            else -> OfficeDocumentTextExtractor.extract(file, mime)
        }
    }
}
