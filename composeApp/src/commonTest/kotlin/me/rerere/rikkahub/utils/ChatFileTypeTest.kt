package me.rerere.rikkahub.utils

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatFileTypeTest {
    @Test
    fun documentsRetainTheOriginalMimeAndExtensionRules() {
        listOf(
            "report" to "application/pdf",
            "notes" to "text/markdown",
            "source.KT" to "application/octet-stream",
            "config.yml" to "application/octet-stream",
        ).forEach { (name, mime) -> assertTrue(isAllowedFileType(name, mime), name) }

        listOf(
            "photo.png" to "image/png",
            "archive.zip" to "application/zip",
            "program.exe" to "application/octet-stream",
        ).forEach { (name, mime) -> assertFalse(isAllowedFileType(name, mime), name) }
    }
}
