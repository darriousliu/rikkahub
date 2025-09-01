package me.rerere.rikkahub.utils

interface DocumentReader {
    fun extractPdfText(filePath: String): String?
    fun extractDocxText(filePath: String): String?
}
