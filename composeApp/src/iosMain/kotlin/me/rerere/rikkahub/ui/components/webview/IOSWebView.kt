package me.rerere.rikkahub.ui.components.webview

import co.touchlab.kermit.Logger
import platform.Foundation.NSArray
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.Foundation.NSUserDomainMask
import platform.WebKit.WKWebView

class IOSWebView(override val webView: WKWebView) : IWebView {
    override fun loadUrl(url: String, additionalHttpHeaders: Map<String, String>) {
        // Check if it's a file URL
        if (url.startsWith("file://")) {
            val fileURL = NSURL(string = url)
            if (fileURL != null && fileURL.isFileURL()) {
                // Use document directory for read access to fix real device issues
                val documentPaths =
                    NSSearchPathForDirectoriesInDomains(
                        NSDocumentDirectory,
                        NSUserDomainMask,
                        true,
                    ) as NSArray
                val readAccessURL =
                    if (documentPaths.count > 0u) {
                        val documentPath = documentPaths.objectAtIndex(0u) as? String
                        documentPath?.let { NSURL.fileURLWithPath(it) }
                    } else {
                        null
                    }

                if (readAccessURL != null) {
                    webView.loadFileURL(fileURL, readAccessURL)
                    return
                }
            }
        }

        // Handle regular HTTP/HTTPS URLs
        val request =
            NSMutableURLRequest.requestWithURL(
                URL = NSURL(string = url),
            )
        webView.loadRequest(
            request = request,
        )
    }

    override fun loadData(
        html: String?,
        baseUrl: String?,
        mimeType: String?,
        encoding: String?,
        historyUrl: String?
    ) {
        if (html == null) {
            Logger.e {
                "LoadHtml: html is null"
            }
            return
        }
        webView.loadHTMLString(
            string = html,
            baseURL = baseUrl?.let { NSURL.URLWithString(it) },
        )
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
        return webView.canGoBack
    }

    override fun canGoForward(): Boolean {
        return webView.canGoForward
    }

    override fun evaluateJavascript(script: String, callback: ((String) -> Unit)?) {
        webView.evaluateJavaScript(script) { result, error ->
            if (callback == null) return@evaluateJavaScript
            if (error != null) {
                Logger.e { "evaluateJavaScript error: $error" }
                callback.invoke(error.localizedDescription())
            } else {
                callback.invoke(result?.toString() ?: "")
            }
        }
    }

    override fun stopLoading() {
        webView.stopLoading()
    }

    override fun clearHistory() {
        // iOS does not provide a direct method to clear history
        // A common workaround is to load a blank page
        webView.loadRequest(NSURLRequest.requestWithURL(NSURL.URLWithString("about:blank")!!))
    }
}
