package me.rerere.rikkahub.ui.components.message

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.platform.ExternalUriOpener
import me.rerere.rikkahub.ui.components.richtext.buildSharedMarkdownPreviewHtml
import me.rerere.rikkahub.ui.components.webview.JvmWebViewContentStore
import me.rerere.rikkahub.ui.context.Navigator

class JvmChatMessagePlatformActions(
    private val externalUriOpener: ExternalUriOpener,
) : ChatMessagePlatformActions {
    override fun openAttachment(uri: String): Result<Unit> = externalUriOpener.open(uri)

    override fun openMarkdownPreview(
        markdown: String,
        colorScheme: ColorScheme,
        navigator: Navigator,
    ): Result<Unit> = runCatching {
        val contentId = JvmWebViewContentStore.store(
            buildSharedMarkdownPreviewHtml(markdown, colorScheme),
        )
        navigator.navigate(Screen.WebView(contentId = contentId))
    }

    @Composable
    override fun RenderEditedFiles(
        parts: List<UIMessagePart>,
        assistant: Assistant?,
    ) = Unit
}
