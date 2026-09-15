package me.rerere.rikkahub.data.ai.transformers

import io.github.vinceglb.filekit.PlatformFile
import platform.Foundation.NSURL

/** Implemented by the Swift application using PDFKit. */
fun interface PdfTextExtractor {
    @Throws(Exception::class)
    fun extract(url: NSURL): String
}

class IosDocumentTextExtractor(private val pdfTextExtractor: PdfTextExtractor) : DocumentTextExtractor {
    override suspend fun extract(file: PlatformFile, mime: String): String? = when (mime) {
        "application/pdf" -> pdfTextExtractor.extract(file.nsUrl)
        else -> OfficeDocumentTextExtractor.extract(file, mime)
    }
}
