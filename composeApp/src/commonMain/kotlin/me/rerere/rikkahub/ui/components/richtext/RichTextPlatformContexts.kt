package me.rerere.rikkahub.ui.components.richtext

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

typealias CodeBlockPreviewRenderer = @Composable (
    code: String,
    language: String,
    modifier: Modifier,
) -> Unit

typealias MermaidRenderer = @Composable (
    code: String,
    modifier: Modifier,
) -> Unit

data class RichTextPlatformActions(
    val saveCode: ((suggestedName: String, code: String) -> Unit)? = null,
    val openCodePreview: ((code: String, language: String) -> Unit)? = null,
    val codeBlockPreviewRenderer: CodeBlockPreviewRenderer? = null,
    val mermaidRenderer: MermaidRenderer? = null,
)

val LocalRichTextPlatformActions = staticCompositionLocalOf { RichTextPlatformActions() }
