package me.rerere.rikkahub.ui.components.webview

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import dev.nucleusframework.webview.web.WebContent
import dev.nucleusframework.webview.web.WebViewState
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import me.rerere.rikkahub.ui.pages.chat.LocalWebViewSnapshots
import org.jetbrains.skia.Image as SkiaImage
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIColor
import platform.UIKit.UIImagePNGRepresentation
import platform.WebKit.WKContentWorld
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.darwin.NSObject
import platform.posix.memcpy
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Composable
internal actual fun ExportWebView(state: WebViewState, modifier: Modifier) {
    val snapshots = LocalWebViewSnapshots.current
    val density = LocalDensity.current.density
    var size by remember { mutableStateOf(IntSize.Zero) }
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    val content = state.content
    LaunchedEffect(content, size) {
        if (size.width == 0 || size.height == 0) return@LaunchedEffect
        val completed = CompletableDeferred<Unit>()
        snapshots[state] = completed
        try {
            val data = content as WebContent.Data // Inline HTML, SVG and Mermaid all use HTML data.
            bitmap = captureHtml(data, size.width / density, size.height / density)
            completed.complete(Unit)
        } catch (e: Exception) {
            completed.completeExceptionally(e)
            if (e is CancellationException) throw e
        }
    }
    Box(modifier.fillMaxWidth().onSizeChanged { size = it }) {
        bitmap?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
    }
}

@OptIn(ExperimentalForeignApi::class)
private suspend fun captureHtml(content: WebContent.Data, width: Float, height: Float): ImageBitmap =
    withTimeoutOrNull(10_000) {
        val view = WKWebView(CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()), WKWebViewConfiguration())
        view.setOpaque(false)
        view.setBackgroundColor(UIColor.clearColor)
        view.scrollView.setBackgroundColor(UIColor.clearColor)
        val delegate = object : NSObject(), WKNavigationDelegateProtocol {
            val loaded = CompletableDeferred<Unit>()

            override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
                loaded.complete(Unit)
            }

            @ObjCSignatureOverride
            override fun webView(webView: WKWebView, didFailNavigation: WKNavigation?, withError: NSError) {
                loaded.completeExceptionally(IllegalStateException(withError.localizedDescription))
            }

            @ObjCSignatureOverride
            override fun webView(webView: WKWebView, didFailProvisionalNavigation: WKNavigation?, withError: NSError) {
                loaded.completeExceptionally(IllegalStateException(withError.localizedDescription))
            }
        }
        view.navigationDelegate = delegate
        view.userInteractionEnabled = false
        // Keep WebKit attached for rendering, behind the app's existing content.
        val window = checkNotNull(UIApplication.sharedApplication.keyWindow)
        window.insertSubview(view, atIndex = 0)
        try {
            view.loadHTMLString(content.data, content.baseUrl?.let { NSURL.URLWithString(it) })
            delegate.loaded.await()
            suspendCancellableCoroutine<Unit> { continuation ->
                view.callAsyncJavaScript(
                    """
                    await document.fonts.ready;
                    await Promise.all(Array.from(document.images).map(img => img.complete ? Promise.resolve() :
                        new Promise(resolve => { img.onload = resolve; img.onerror = resolve; })));
                    while (window.mermaid && document.querySelector('.mermaid') && !document.querySelector('.mermaid svg g')) {
                        await new Promise(resolve => setTimeout(resolve, 20));
                    }
                    await new Promise(resolve => setTimeout(resolve, 50));
                    """.trimIndent(),
                    arguments = emptyMap<Any?, Any?>(),
                    inFrame = null,
                    inContentWorld = WKContentWorld.pageWorld,
                ) { _, error ->
                    if (continuation.isActive) {
                        if (error == null) continuation.resume(Unit)
                        else continuation.resumeWithException(IllegalStateException(error.localizedDescription))
                    }
                }
            }
            val bytes = suspendCancellableCoroutine<ByteArray> { continuation ->
                view.takeSnapshotWithConfiguration(null) { image, error ->
                    if (continuation.isActive) {
                        val data = image?.let { UIImagePNGRepresentation(it) }
                        if (data == null) {
                            continuation.resumeWithException(IllegalStateException(error?.localizedDescription ?: "Empty WebView snapshot"))
                        } else {
                            val result = ByteArray(data.length.toInt())
                            result.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
                            continuation.resume(result)
                        }
                    }
                }
            }
            SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
        } finally {
            // WKWebView holds its delegate weakly; retain it through loading and cleanup.
            delegate.loaded.cancel()
            view.stopLoading()
            view.navigationDelegate = null
            view.removeFromSuperview()
        }
    } ?: error("Timed out capturing WebView")
