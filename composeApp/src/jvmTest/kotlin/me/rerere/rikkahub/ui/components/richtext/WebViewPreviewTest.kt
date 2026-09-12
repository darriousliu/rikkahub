package me.rerere.rikkahub.ui.components.richtext

import androidx.compose.material3.lightColorScheme
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebViewPreviewTest {
    @Test
    fun htmlPreviewKeepsTheOriginalDocumentAndScripts() {
        val html = "<!doctype html><title>预览</title><script>document.body.textContent='rendered'</script>"
        assertEquals(html, buildCodePreviewHtml(html, "html"))
        val svg = "<svg><text>x &amp; y</text></svg>"
        assertEquals(
            "<!DOCTYPE html><html><body style=\"margin:0;display:flex;justify-content:center;align-items:center;min-height:100vh;\">$svg</body></html>",
            buildCodePreviewHtml(svg, "svg"),
        )
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

    @Test
    fun mermaidLoadsBundledScriptAndKeepsRenderingAndExportBody() {
        val html = buildMermaidHtml("graph TD; A[<label>]-->B", lightColorScheme(), "/* bundled mermaid */")
        assertTrue(html.contains("<script>/* bundled mermaid */</script>"))
        assertFalse(html.contains("<script src="))
        assertTrue(html.contains("A[&lt;label&gt;]"))
        assertTrue(html.contains("window.kmpJsBridge.callNative('exportImage', pngBase64)"))
        assertTrue(html.contains("ctx.fillText('rikka-ai.com'"))
    }
}
