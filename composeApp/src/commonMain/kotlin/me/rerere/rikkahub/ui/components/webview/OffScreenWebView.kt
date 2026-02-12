package me.rerere.rikkahub.ui.components.webview

object OffScreenWebViewManager {
    val webViewState: WebViewState = WebViewState()

    var webView: IWebView? = null
        private set

    fun init() {
        val iWebView = initGlobalWebView()
        webView = iWebView
        webViewState.webView = iWebView
    }

    /**
     * Add a JavaScript bridge to the off-screen WebView after initialization.
     * On Android, this adds a JavascriptInterface; on iOS, this adds a WKScriptMessageHandler.
     */
    fun addJsBridge(name: String, bridge: Any) {
        addJsBridgePlatform(name, bridge)
    }

    /**
     * Evaluate JavaScript in the off-screen WebView.
     */
    fun evaluateJavascript(script: String, callback: ((String) -> Unit)? = null) {
        webView?.evaluateJavascript(script, callback)
    }
}

expect fun initGlobalWebView(): IWebView

/**
 * Platform-specific implementation for adding a JS bridge to the off-screen WebView.
 */
expect fun addJsBridgePlatform(name: String, bridge: Any)
