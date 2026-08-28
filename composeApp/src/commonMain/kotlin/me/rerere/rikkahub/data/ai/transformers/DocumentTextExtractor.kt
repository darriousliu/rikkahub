package me.rerere.rikkahub.data.ai.transformers

import io.github.vinceglb.filekit.PlatformFile

/**
 * Extracts text from binary document formats such as PDF or DOCX.
 *
 * Returning null means this platform ships no parser for [mime]; the caller then falls back to
 * reading the file as plain text, or reports the format as unsupported.
 */
fun interface DocumentTextExtractor {
    suspend fun extract(file: PlatformFile, mime: String): String?
}

/** Used by shells that bundle no document parsers. */
object UnsupportedDocumentTextExtractor : DocumentTextExtractor {
    override suspend fun extract(file: PlatformFile, mime: String): String? = null
}
