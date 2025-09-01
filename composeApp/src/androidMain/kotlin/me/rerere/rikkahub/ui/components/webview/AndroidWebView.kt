package me.rerere.rikkahub.ui.components.webview

import android.webkit.WebView

class AndroidWebView(
    override val webView: WebView,
) : IWebView {
    override fun loadUrl(url: String, additionalHttpHeaders: Map<String, String>) {
        webView.loadUrl(url, additionalHttpHeaders)
    }

    override fun loadData(
        html: String?,
        baseUrl: String?,
        mimeType: String?,
        encoding: String?,
        historyUrl: String?
    ) {
        if (html == null) return
        webView.loadDataWithBaseURL(baseUrl, html, mimeType, encoding, historyUrl)
    }

    override fun reload() {
        webView.reload()
    }

    override fun goBack() {
        webView.goBack()
    }

    override fun goForward() {
        webView.goForward()
    }

    override fun canGoBack(): Boolean {
        return webView.canGoBack()
    }

    override fun canGoForward(): Boolean {
        return webView.canGoForward()
    }

    override fun evaluateJavascript(script: String, callback: ((String) -> Unit)?) {
        webView.evaluateJavascript(script) { result ->
            callback?.invoke(result)
        }
    }

    override fun stopLoading() {
        webView.stopLoading()
    }

    override fun clearHistory() {
        webView.clearHistory()
    }
}
