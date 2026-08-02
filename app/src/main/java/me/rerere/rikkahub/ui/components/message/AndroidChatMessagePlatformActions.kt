package me.rerere.rikkahub.ui.components.message

import android.content.Context
import android.content.Intent
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.core.content.FileProvider
import androidx.core.net.toFile
import androidx.core.net.toUri
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.components.richtext.buildMarkdownPreviewHtml
import me.rerere.rikkahub.ui.components.webview.WebViewContentCache
import me.rerere.rikkahub.ui.context.Navigator

class AndroidChatMessagePlatformActions(
    private val context: Context,
) : ChatMessagePlatformActions {
    override fun openAttachment(uri: String): Result<Unit> = runCatching {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            data = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                uri.toUri().toFile(),
            )
        }
        context.startActivity(Intent.createChooser(intent, null))
    }

    override fun openMarkdownPreview(
        markdown: String,
        colorScheme: ColorScheme,
        navigator: Navigator,
    ): Result<Unit> = runCatching {
        val htmlContent = buildMarkdownPreviewHtml(
            context = context,
            markdown = markdown,
            colorScheme = colorScheme,
        )
        val contentId = WebViewContentCache.store(context.cacheDir, htmlContent)
        navigator.navigate(Screen.WebView(contentId = contentId))
    }

    @Composable
    override fun RenderEditedFiles(
        parts: List<UIMessagePart>,
        assistant: Assistant?,
    ) {
        EditedFilesList(parts = parts, assistant = assistant)
    }
}
