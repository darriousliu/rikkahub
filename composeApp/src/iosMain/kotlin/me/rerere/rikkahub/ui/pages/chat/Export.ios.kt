package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.ui.unit.Density
import coil3.Uri
import kotlinx.coroutines.CoroutineScope
import me.rerere.ai.ui.UIMessage
import me.rerere.common.PlatformContext
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.utils.ShareUtil

internal actual suspend fun exportToImage(
    context: PlatformContext,
    scope: CoroutineScope,
    density: Density,
    conversation: Conversation,
    messages: List<UIMessage>,
    settings: Settings,
    options: ImageExportOptions
) {
    // TODO("Not yet implemented")
}

internal actual fun shareFile(context: PlatformContext, uri: Uri, mimeType: String) {
    ShareUtil.shareFile(shareUri = uri.toString())
}
