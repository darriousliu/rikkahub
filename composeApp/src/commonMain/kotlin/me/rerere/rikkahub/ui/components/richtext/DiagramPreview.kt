package me.rerere.rikkahub.ui.components.richtext

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.asPainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.SuccessResult
import com.dokar.sonner.ToastType
import com.jvziyaoyao.scale.image.pager.ImagePager
import com.jvziyaoyao.scale.zoomable.pager.rememberZoomablePagerState
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.compose.rememberFileSaverLauncher
import io.github.vinceglb.filekit.write
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Download01
import me.rerere.hugeicons.stroke.View
import me.rerere.rikkahub.generated.resources.*
import me.rerere.rikkahub.platform.encodeImageBitmapToPng
import me.rerere.rikkahub.ui.components.ui.LocalDiagramRenders
import me.rerere.rikkahub.ui.components.ui.LocalExportContext
import me.rerere.rikkahub.ui.context.LocalToaster
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

@Composable
internal fun DiagramPreview(diagram: DiagramSource, modifier: Modifier = Modifier) {
    var preview by remember(diagram) { mutableStateOf(false) }
    Column(modifier) {
        NativeDiagramImage(diagram, Modifier.fillMaxWidth().height(200.dp))
        if (!LocalExportContext.current) {
            Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = { preview = true }) {
                    Icon(HugeIcons.View, contentDescription = stringResource(Res.string.code_block_preview))
                }
                DiagramDownloadButton(diagram)
            }
        }
    }
    if (preview) DiagramPreviewDialog(diagram) { preview = false }
}

@Composable
internal fun NativeDiagramImage(diagram: DiagramSource, modifier: Modifier = Modifier) {
    val context = LocalPlatformContext.current
    val request = remember(diagram, context) { diagramImageRequest(context, diagram).build() }
    var loading by remember(request) { mutableStateOf(true) }
    var error by remember(request) { mutableStateOf<String?>(null) }
    val renders = LocalDiagramRenders.current
    val ready = remember(request) { CompletableDeferred<Unit>() }
    val fitWidth = diagram.language == "mermaid"
    val scrollState = rememberScrollState()
    DisposableEffect(renders, ready) {
        renders?.add(ready)
        onDispose { renders?.remove(ready) }
    }
    Box(modifier.testTag("native-diagram-viewport")) {
        AsyncImage(
            model = request,
            contentDescription = "${diagram.language} diagram",
            contentScale = if (fitWidth) ContentScale.FillWidth else ContentScale.Fit,
            alignment = if (fitWidth) Alignment.TopCenter else Alignment.Center,
            // The viewport stays 200 dp tall; Mermaid keeps its aspect ratio at the available width.
            modifier = (if (fitWidth) Modifier.fillMaxWidth().verticalScroll(scrollState) else Modifier.fillMaxSize())
                .testTag("native-diagram-image")
                .semantics {
                    stateDescription = if (loading) "Loading" else if (error != null) "Error" else "Ready"
                },
            onSuccess = {
                loading = false
                error = null
                ready.complete(Unit)
            },
            onError = {
                loading = false
                error = it.result.throwable.message ?: "Unable to render diagram"
                ready.complete(Unit)
            },
        )
        if (loading) CircularProgressIndicator(Modifier.align(Alignment.Center).size(24.dp))
        error?.let {
            Text(
                it,
                modifier = Modifier.align(Alignment.Center).padding(8.dp).testTag("native-diagram-error"),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
internal fun DiagramPreviewDialog(diagram: DiagramSource, onDismiss: () -> Unit) {
    val context = LocalPlatformContext.current
    val request = remember(diagram, context) { diagramImageRequest(context, diagram).size(2048, 2048).build() }
    val painter = rememberAsyncImagePainter(request)
    val state by painter.state.collectAsState()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("native-diagram-preview")) {
            ImagePager(
                modifier = Modifier.fillMaxSize(),
                pagerState = rememberZoomablePagerState { 1 },
                imageLoader = {
                    painter to painter.intrinsicSize
                },
            )
            when (val current = state) {
                is AsyncImagePainter.State.Empty, is AsyncImagePainter.State.Loading -> {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
                is AsyncImagePainter.State.Error -> {
                    Text(current.result.throwable.message ?: "Unable to render diagram",
                        Modifier.align(Alignment.Center).padding(24.dp), color = MaterialTheme.colorScheme.error)
                }
                is AsyncImagePainter.State.Success -> Unit
            }
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                Icon(HugeIcons.Cancel01, contentDescription = "Close")
            }
            Box(Modifier.align(Alignment.BottomCenter).padding(8.dp)) {
                DiagramDownloadButton(diagram)
            }
        }
    }
}

@Composable
private fun DiagramDownloadButton(diagram: DiagramSource) {
    val context = LocalPlatformContext.current
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val colors = MaterialTheme.colorScheme
    val textMeasurer = rememberTextMeasurer()
    val success = stringResource(Res.string.mermaid_export_success)
    val failure = stringResource(Res.string.mermaid_export_failed)
    var pending by remember { mutableStateOf<ByteArray?>(null) }
    var exporting by remember { mutableStateOf(false) }
    val saveLauncher = rememberFileSaverLauncher(dialogSettings = FileKitDialogSettings.createDefault()) { file ->
        val bytes = pending
        pending = null
        if (file != null && bytes != null) scope.launch {
            try {
                file.write(bytes)
                toaster.show(success, type = ToastType.Success)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                toaster.show(failure, type = ToastType.Error)
            }
        }
    }
    IconButton(enabled = !exporting, onClick = {
        scope.launch {
            exporting = true
            try {
                val result = SingletonImageLoader.get(context).execute(
                    diagramImageRequest(context, diagram).size(2048, 2048).build(),
                )
                check(result is SuccessResult) { "Unable to render diagram" }
                val watermark = textMeasurer.measure("rikka-ai.com", TextStyle(fontSize = 14.sp))
                pending = withContext(Dispatchers.Default) {
                    val image = result.image
                    val bitmap = ImageBitmap(image.width, image.height)
                    CanvasDrawScope().draw(
                        Density(1f), LayoutDirection.Ltr, Canvas(bitmap),
                        Size(image.width.toFloat(), image.height.toFloat()),
                    ) {
                        drawRect(colors.background)
                        with(image.asPainter(context)) { draw(size) }
                        drawText(
                            watermark, colors.onBackground,
                            topLeft = Offset(20f, size.height - watermark.size.height - 10f),
                        )
                    }
                    encodeImageBitmapToPng(bitmap)
                }
                saveLauncher.launch("${diagram.language}_${Clock.System.now().toEpochMilliseconds()}", "png")
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                toaster.show(failure, type = ToastType.Error)
            } finally {
                exporting = false
            }
        }
    }) {
        Icon(HugeIcons.Download01, contentDescription = stringResource(Res.string.mermaid_export))
    }
}
