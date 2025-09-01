package me.rerere.rikkahub.ui.components.webview


interface IWebView {
    val webView: NativeWebView
    fun loadUrl(url: String, additionalHttpHeaders: Map<String, String> = emptyMap())

    fun loadData(
        html: String?,
        baseUrl: String?,
        mimeType: String?,
        encoding: String?,
        historyUrl: String?,
    )

    fun reload()
    fun goBack()
    fun goForward()
    fun canGoBack(): Boolean
    fun canGoForward(): Boolean
    fun evaluateJavascript(script: String, callback: ((String) -> Unit)? = null)
    fun stopLoading()
    fun clearHistory()
}
