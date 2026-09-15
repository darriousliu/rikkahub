package me.rerere.document

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import java.io.File

object PdfParser {
    fun parserPdf(file: File): String = Loader.loadPDF(file).use { document ->
        val stripper = PDFTextStripper().apply {
            lineSeparator = "\n"
            pageEnd = "\n"
        }
        val result = StringBuilder()
        for (i in 0 until document.numberOfPages) {
            stripper.startPage = i + 1
            stripper.endPage = i + 1
            result.append("---")
            result.append("Page ${i + 1}:\n")
            result.append(stripper.getText(document))
            result.appendLine()
        }
        result.toString()
    }
}
