package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.platform.FrameRecomposer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import dev.nucleusframework.webview.web.WebViewState
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import me.rerere.rikkahub.ui.components.ui.LocalExportContext
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Surface
import org.jetbrains.skia.impl.use
import kotlin.time.TimeSource

internal val LocalWebViewSnapshots = staticCompositionLocalOf<MutableMap<WebViewState, Deferred<Unit>>> {
    error("WebView snapshots require an image export composition")
}

@OptIn(InternalComposeUiApi::class)
internal suspend fun renderComposeImage(
    compositionLocalContext: CompositionLocalContext,
    screenDensity: Density,
    content: @Composable () -> Unit,
): ByteArray = withContext(Dispatchers.Main) {
    // The original layout is 540 dp wide. Use the same density for measurement and drawing.
    val exportDensity = Density(2f, screenDensity.fontScale)
    val webViewSnapshots = mutableMapOf<WebViewState, Deferred<Unit>>()
    val started = TimeSource.Monotonic.markNow()
    val recomposer = FrameRecomposer(coroutineContext)
    val scene = CanvasLayersComposeScene(frameRecomposer = recomposer, density = exportDensity)
    try {
        scene.compositionLocalContext = compositionLocalContext
        scene.setContent {
            CompositionLocalProvider(
                LocalDensity provides exportDensity,
                LocalExportContext provides true,
                LocalWebViewSnapshots provides webViewSnapshots,
                content = content,
            )
        }
        val constraints = Constraints.fixedWidth(1080)
        recomposer.performFrame(started.elapsedNow().inWholeNanoseconds)
        scene.measureContent(constraints)
        // Match BitmapComposer's wait for asynchronous image/resource composition.
        delay(100)
        recomposer.performFrame(started.elapsedNow().inWholeNanoseconds)
        scene.measureContent(constraints)
        yield() // Start snapshot effects after their preview sizes are known.
        withTimeout(15_000) {
            while (webViewSnapshots.values.any { !it.isCompleted }) {
                delay(16)
                recomposer.performFrame(started.elapsedNow().inWholeNanoseconds)
                scene.measureContent(constraints)
            }
            webViewSnapshots.values.forEach { it.await() }
        }
        // Native Tao and Compose's AWT snapshot dispatcher can be on different threads.
        // Deliver completed snapshot state changes before drawing the final offscreen frame.
        Snapshot.sendApplyNotifications()
        recomposer.performFrame(started.elapsedNow().inWholeNanoseconds)
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
