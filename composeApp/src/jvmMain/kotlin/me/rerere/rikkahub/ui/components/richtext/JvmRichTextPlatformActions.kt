package me.rerere.rikkahub.ui.components.richtext

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.components.webview.JvmWebViewContentStore
import me.rerere.rikkahub.ui.context.Navigator

@Composable
fun rememberJvmRichTextPlatformActions(navigator: Navigator): RichTextPlatformActions = remember(navigator) {
    RichTextPlatformActions(
        openCodePreview = { code, language ->
            val contentId = JvmWebViewContentStore.store(buildCodePreviewHtml(code, language))
            navigator.navigate(Screen.WebView(contentId = contentId))
        },
    )
}

private fun buildCodePreviewHtml(code: String, language: String): String = if (language == "svg") {
    """<!DOCTYPE html><html><body style="margin:0">$code</body></html>"""
} else {
    """
        <!DOCTYPE html>
        <html>
        <head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"></head>
        <body><pre style="white-space:pre-wrap;word-break:break-word"><code>${code.escapeHtml()}</code></pre></body>
        </html>
    """.trimIndent()
}

private fun String.escapeHtml(): String = buildString(length) {
    for (character in this@escapeHtml) {
        append(
            when (character) {
                '&' -> "&amp;"
                '<' -> "&lt;"
                '>' -> "&gt;"
                '"' -> "&quot;"
                '\'' -> "&#39;"
                else -> character
            },
        )
    }
}
