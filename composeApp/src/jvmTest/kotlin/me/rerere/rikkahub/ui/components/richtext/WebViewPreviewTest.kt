package me.rerere.rikkahub.ui.components.richtext

import androidx.compose.material3.lightColorScheme
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebViewPreviewTest {
    @Test
    fun windowsHtmlKeepsSourceAndNativeDiagramsStillPreview() {
        assertFalse(canInlineCodePreview("html", complete = true, isWindows = true))
        assertTrue(canInlineCodePreview("html", complete = true, isWindows = false))
        for (language in listOf("svg", "mermaid")) {
            assertTrue(canInlineCodePreview(language, complete = true, isWindows = true))
            assertTrue(canInlineCodePreview(language, complete = true, isWindows = false))
            assertFalse(canInlineCodePreview(language, complete = false, isWindows = false))
        }
        assertFalse(canInlineCodePreview("html", complete = false, isWindows = false))
        assertFalse(canInlineCodePreview("kotlin", complete = true, isWindows = false))
    }

    @Test
    fun markdownPreviewUsesOriginalTemplateAndPreservesUnicodeSource() = runTest {
        val markdown = "# 中文\n\n\$x^2\$\n\n```mermaid\ngraph TD; A-->B\n```"
        val html = buildMarkdownPreviewHtml(markdown, lightColorScheme())
        assertTrue(html.contains(Base64.encode(markdown.encodeToByteArray())))
        assertTrue(html.contains("katex"))
        assertTrue(html.contains("mermaid"))
        assertTrue(html.contains("highlight"))
        assertFalse(html.contains("{{MARKDOWN_BASE64}}"))
        assertFalse(html.contains("{{BACKGROUND_COLOR}}"))
    }

}
