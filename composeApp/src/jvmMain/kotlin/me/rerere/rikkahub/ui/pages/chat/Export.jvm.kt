package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.platform.FrameRecomposer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import coil3.PlatformContext
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.openFileSaver
import io.github.vinceglb.filekit.write
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import me.rerere.ai.ui.UIMessage
import me.rerere.common.time.toDashedFileTimestamp
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.ui.components.ui.LocalExportContext
import me.rerere.rikkahub.ui.context.LocalSettings
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Surface
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

@OptIn(InternalComposeUiApi::class)
internal suspend fun renderComposeImage(
    compositionLocalContext: CompositionLocalContext,
    screenDensity: Density,
    content: @Composable () -> Unit,
): ByteArray = withContext(Dispatchers.Main) {
    // The original layout is 540 dp wide. Use the same density for measurement and drawing.
    val exportDensity = Density(2f, screenDensity.fontScale)
    val recomposer = FrameRecomposer(coroutineContext)
    val scene = CanvasLayersComposeScene(frameRecomposer = recomposer, density = exportDensity)
    try {
        scene.compositionLocalContext = compositionLocalContext
        scene.setContent {
            CompositionLocalProvider(
                LocalDensity provides exportDensity,
                LocalExportContext provides true,
                content = content,
            )
        }
        val constraints = Constraints.fixedWidth(1080)
        recomposer.performFrame(System.nanoTime())
        scene.measureContent(constraints)
        // Match BitmapComposer's wait for asynchronous image/resource composition.
        delay(100)
        recomposer.performFrame(System.nanoTime())
        val size = scene.measureContent(constraints)
        scene.size = size
        scene.measureAndLayout()
        Surface.makeRasterN32Premul(size.width, size.height).use { surface ->
            scene.draw(surface.canvas.asComposeCanvas())
            surface.makeImageSnapshot().use { image ->
                checkNotNull(image.encodeToData(EncodedImageFormat.PNG)).use { it.bytes }
            }
        }
    } finally {
        scene.close()
        recomposer.close()
    }
}
