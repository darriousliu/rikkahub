package me.rerere.rikkahub.ui.components.webview

import android.webkit.ConsoleMessage
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import io.github.kdroidfilter.webview.web.NativeWebView
import io.github.kdroidfilter.webview.web.WebViewFactoryParam

internal actual fun createWebView(
    param: WebViewFactoryParam,
    onConsoleMessage: (WebViewConsoleMessage) -> Unit,
): NativeWebView = object : WebView(param.context) {
    override fun evaluateJavascript(script: String, resultCallback: ValueCallback<String>?) {
        // composewebview beta-02 injects viewport/selection CSS before <head> exists.
        // Preserve the document's own viewport and selectable text, as in 2.4.5.
        if (script.startsWith("var meta = document.createElement('meta');") ||
            script.contains("-webkit-tap-highlight-color: transparent; -webkit-touch-callout: none; -webkit-user-select: none;")
        ) {
            resultCallback?.onReceiveValue("null")
            return
        }
        super.evaluateJavascript(script, resultCallback)
    }

    override fun setWebChromeClient(client: WebChromeClient?) {
        super.setWebChromeClient(client?.let { delegate ->
            object : WebChromeClient() {
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
        })
    }
}.apply {
    settings.apply {
        domStorageEnabled = true
        allowContentAccess = true
        builtInZoomControls = true
        displayZoomControls = false
        loadWithOverviewMode = true
    }
}

internal actual fun disposeWebView(view: NativeWebView) = Unit
