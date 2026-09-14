package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.runtime.CompositionLocalContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.Density
import coil3.PlatformContext
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.openFileSaver
import io.github.vinceglb.filekit.write
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import me.rerere.ai.ui.UIMessage
import me.rerere.common.time.toDashedFileTimestamp
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.ui.context.LocalSettings
import kotlin.time.Clock

internal actual suspend fun exportToImage(
    context: PlatformContext,
    compositionLocalContext: CompositionLocalContext,
    scope: CoroutineScope,
    density: Density,
    conversation: Conversation,
    messages: List<UIMessage>,
    settings: Settings,
    options: ImageExportOptions,
) {
    val target = FileKit.openFileSaver(
        suggestedName = "chat-export-${Clock.System.now().toDashedFileTimestamp()}",
        defaultExtension = "png",
        allowedExtensions = setOf("png"),
    ) ?: throw CancellationException("Image save cancelled")
    val png = renderComposeImage(compositionLocalContext, density) {
        CompositionLocalProvider(LocalSettings provides settings) {
            ExportedChatImage(conversation, messages, options)
        }
    }
    target.write(png)
}
