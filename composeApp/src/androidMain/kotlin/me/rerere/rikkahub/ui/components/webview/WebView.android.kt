package me.rerere.rikkahub.ui.components.webview

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup.LayoutParams
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import co.touchlab.kermit.Logger
import android.webkit.ConsoleMessage as AndroidConsoleMessage

actual typealias NativeWebView = WebView

actual typealias WebSettings = android.webkit.WebSettings

actual typealias ConsoleMessage = AndroidConsoleMessage

private const val TAG = "WebView"

internal class MyWebChromeClient(private val state: WebViewState) : WebChromeClient() {
    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        state.loadingProgress = newProgress / 100f
    }

    override fun onReceivedTitle(view: WebView?, title: String?) {
        super.onReceivedTitle(view, title)
        state.pageTitle = title
    }

    override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
        state.pushConsoleMessage(consoleMessage)
        if (consoleMessage.messageLevel() == AndroidConsoleMessage.MessageLevel.ERROR || consoleMessage.messageLevel() == AndroidConsoleMessage.MessageLevel.WARNING) {
            Logger.e(TAG) { "onConsoleMessage:  ${consoleMessage.message()}  ${consoleMessage.lineNumber()}  ${consoleMessage.sourceId()}" }
        }
        return super.onConsoleMessage(consoleMessage)
    }
}

internal class MyWebViewClient(private val state: WebViewState) : WebViewClient() {
    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        state.isLoading = true
        state.currentUrl = url // Update current URL
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        state.isLoading = false
        state.loadingProgress = 0f // Reset progress when finished
        state.pageTitle = view?.title // Update title
        state.canGoBack = view?.canGoBack() == true
        state.canGoForward = view?.canGoForward() == true
    }
}

actual fun WebSettings.configureZoom() {
    this.apply {
        builtInZoomControls = true
        displayZoomControls = false
    }
}

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
actual fun WebView(
    state: WebViewState,
    modifier: Modifier,
    onCreated: (NativeWebView) -> Unit,
    onUpdated: (NativeWebView) -> Unit,
) {
    // Remember the clients based on the state
    val webChromeClient = remember { MyWebChromeClient(state) }
    val webViewClient = remember { MyWebViewClient(state) }

    Box(
        modifier = modifier
    ) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    layoutParams = LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        LayoutParams.MATCH_PARENT
                    )

                    state.webView = AndroidWebView(this) // Assign the WebView instance to the state

                    onCreated(this)

                    settings.javaScriptEnabled = true // Enable JavaScript
                    settings.domStorageEnabled = true
                    settings.allowContentAccess = true
                    settings.apply(state.settings)

                    // Use the created clients
                    this.webChromeClient = webChromeClient
                    this.webViewClient = webViewClient

                    state.androidInterfaces.forEach { (name, obj) ->
                        addJavascriptInterface(obj, name)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(), // Make WebView fill the width
            onReset = {
                state.androidInterfaces.forEach { (name, _) ->
                    it.removeJavascriptInterface(name)
                }
                Logger.d(TAG) { "AndroidView: Resetting WebView" }
            },
            update = { webView ->
                state.webView = AndroidWebView(webView)
                state.androidInterfaces.forEach { (name, obj) ->
                    webView.addJavascriptInterface(obj, name)
                }
                Logger.d(TAG) { "AndroidView: Updating WebView" }
                // Ensure clients are updated if state changes (though unlikely here)
                // webView.webChromeClient = webChromeClient
                // webView.webViewClient = webViewClient

                // Update settings that might change
                webView.settings.javaScriptEnabled = state.javaScriptEnabled

                when (val content = state.content) {
                    is WebContent.Url -> {
                        val url = content.url
                        // Only load new URL if it's different from the current one or if the state forces reload
                        // Also check if the webView's url is null or blank, which might happen initially
                        val currentWebViewUrl = webView.url
                        if (url.isNotEmpty() && (currentWebViewUrl.isNullOrBlank() || url != currentWebViewUrl || state.forceReload)) {
                            webView.loadUrl(content.url, content.additionalHttpHeaders)
                            state.forceReload = false // Reset force reload flag
                        }
                    }

                    is WebContent.Data -> {
                        // Check if the data needs to be reloaded (e.g., if different from last loaded data)
                        // For simplicity, we might just reload it every time the update block runs with Data content.
                        // A more complex check could involve comparing `content.data` with a previously stored value.
                        webView.loadDataWithBaseURL(
                            content.baseUrl,
                            content.data,
                            content.mimeType,
                            content.encoding,
                            content.historyUrl
                        )
                        // Assuming data loading is fast, but let's reflect the state more accurately
                        // state.isLoading = false // This might be too soon, let WebViewClient handle it
                    }

                    WebContent.NavigatorOnly -> {
                        // NO-OP: State changes related to navigation are handled by the methods in WebViewState
                    }
                }
                onUpdated(webView)
            }
        )

        // Loading Progress Indicator
        if (state.isLoading) {
            LinearProgressIndicator(
                progress = { state.loadingProgress },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

actual fun ConsoleMessage.messageLevelName(): String {
    return messageLevel().name
}

actual fun ConsoleMessage.message(): String {
    return message()
}

actual fun ConsoleMessage.sourceId(): String {
    return sourceId()
}

actual fun ConsoleMessage.lineNumber(): Int {
    return lineNumber()
}
