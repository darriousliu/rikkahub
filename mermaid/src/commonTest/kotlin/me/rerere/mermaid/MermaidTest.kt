package me.rerere.mermaid

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MermaidTest {
    @Test
    fun appliesThemeWithoutChangingSourceFrontmatter() {
        val source = "---\ntitle: 主题\n---\nflowchart LR\n A[开始] --> B[完成]"
        val svg = assertNotNull(renderMermaidSvg(source,
            """{"theme":"base","themeVariables":{"primaryColor":"#f12345"}}"""))
        assertTrue(svg.contains("#f12345"))
        assertTrue(svg.contains("主题"))
        assertFailsWith<MermaidRenderException> { renderMermaidSvg(source, "invalid json") }
    }

    @Test
    fun rendersUnicodeLabelsWithoutBrowserElements() {
        val svg = assertNotNull(renderMermaidSvg("flowchart LR\n A[你好<br/>Mermaid] --> B[完成]"))
        assertTrue(svg.contains("<svg"))
        assertTrue(svg.contains("你好"))
        assertTrue(svg.contains("完成"))
        assertFalse(svg.contains("foreignObject"))
    }

    @Test
    fun rendersMathWithUpstreamDefaultFeatures() {
        val formula = "\$\$x^2\$\$"
        val svg = assertNotNull(renderMermaidSvg("flowchart LR\n A[\"$formula\"]"))
        assertTrue(svg.contains("<svg"))
        assertTrue(svg.contains("<path"))
        assertFalse(svg.contains(formula))
        assertFalse(svg.contains("foreignObject"))
    }

    @Test
    fun preservesUpstreamErrorForEmptySource() {
        assertFailsWith<MermaidRenderException> { renderMermaidSvg("") }
    }

    @Test
    fun errorDoesNotPoisonSubsequentRender() {
        assertFailsWith<MermaidRenderException> { renderMermaidSvg("not a mermaid diagram") }
        assertNotNull(renderMermaidSvg("sequenceDiagram\n Alice->>Bob: Hello"))
    }
}
