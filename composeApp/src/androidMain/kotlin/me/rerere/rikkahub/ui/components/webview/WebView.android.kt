package me.rerere.rikkahub.ui.components.webview

import android.graphics.Bitmap
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import dev.nucleusframework.webview.web.LoadingState
import dev.nucleusframework.webview.web.NativeWebView
import dev.nucleusframework.webview.web.WebViewState

internal actual fun configureWebViewState(state: WebViewState) = Unit

internal actual fun configureNativeWebView(
    view: NativeWebView,
    state: WebViewState,
    onConsoleMessage: (WebViewConsoleMessage) -> Unit,
) {
    view.settings.apply {
        allowContentAccess = true
        builtInZoomControls = true
        displayZoomControls = false
        loadWithOverviewMode = true
    }

    val client = view.webViewClient
    view.webViewClient = object : WebViewClient() {
        @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "INVISIBLE_SETTER")
        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            // Keep 1.0.3's loading state updates, without its viewport/selection CSS injection.
            // The previous custom WebView filtered those scripts in evaluateJavascript.
            // The setters are internal; review this small compatibility shim when upgrading the library.
            state.loadingState = LoadingState.Loading(0f)
            state.errorsForCurrentRequest.clear()
            state.pageTitle = null
            state.lastLoadedUrl = url
        }

        override fun onPageFinished(view: WebView, url: String?) = client.onPageFinished(view, url)

        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) =
            client.doUpdateVisitedHistory(view, url, isReload)

        override fun onReceivedError(view: WebView, request: WebResourceRequest?, error: WebResourceError?) =
            client.onReceivedError(view, request, error)

        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean =
            client.shouldOverrideUrlLoading(view, request)
    }

    view.webChromeClient?.let { delegate ->
        view.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) =
                delegate.onProgressChanged(view, newProgress)

            override fun onReceivedTitle(view: WebView, title: String?) = delegate.onReceivedTitle(view, title)

            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                onConsoleMessage(
                    WebViewConsoleMessage(
                        message.messageLevel().name, message.message(), message.sourceId(), message.lineNumber(),
                    ),
                )
                return delegate.onConsoleMessage(message)
            }
        }
    }
}

internal actual fun disposeWebView(view: NativeWebView) = Unit

@Composable
internal actual fun WebViewLifecycle(content: @Composable ((NativeWebView) -> Unit) -> Unit) {
    content(::disposeWebView)
}
