package me.rerere.rikkahub.ui.components.richtext

import androidx.compose.material3.ColorScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.utils.base64Encode
import me.rerere.rikkahub.utils.toCssHex
import rikkahub.composeapp.generated.resources.Res

/**
 * Build HTML page for markdown preview with support for:
 * - Markdown rendering via marked.js
 * - LaTeX math via KaTeX
 * - Mermaid diagrams
 * - Syntax highlighting via highlight.js
 */
suspend fun buildMarkdownPreviewHtml(markdown: String, colorScheme: ColorScheme): String = withContext(Dispatchers.IO) {
    val htmlTemplate = Res.readBytes("files/html/mark.html").decodeToString()

    htmlTemplate
        .replace("{{MARKDOWN_BASE64}}", markdown.base64Encode())
        .replace("{{BACKGROUND_COLOR}}", colorScheme.background.toCssHex())
        .replace("{{ON_BACKGROUND_COLOR}}", colorScheme.onBackground.toCssHex())
        .replace("{{SURFACE_COLOR}}", colorScheme.surface.toCssHex())
        .replace("{{ON_SURFACE_COLOR}}", colorScheme.onSurface.toCssHex())
        .replace("{{SURFACE_VARIANT_COLOR}}", colorScheme.surfaceVariant.toCssHex())
        .replace("{{ON_SURFACE_VARIANT_COLOR}}", colorScheme.onSurfaceVariant.toCssHex())
        .replace("{{PRIMARY_COLOR}}", colorScheme.primary.toCssHex())
        .replace("{{OUTLINE_COLOR}}", colorScheme.outline.toCssHex())
        .replace("{{OUTLINE_VARIANT_COLOR}}", colorScheme.outlineVariant.toCssHex())
}
